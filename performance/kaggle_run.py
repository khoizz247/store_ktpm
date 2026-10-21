"""Run a reproducible Single Store Team load test inside a fresh Kaggle CPU notebook.

This deliberately refuses to run outside Kaggle or against an existing schema.
It starts MySQL and the Spring Boot JAR directly; Docker is not required there.
"""

import base64
import json
import os
import platform
import secrets
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

from report import build_report


REPO = Path(__file__).resolve().parent.parent
WORKING = Path("/kaggle/working")
HOST = "http://127.0.0.1:18080"
PHASES = (
    ("smoke", 5, 60),
    ("baseline", 10, 120),
    ("main", 30, 300),
    ("high", 60, 300),
)


def run(command, **kwargs):
    print("Running:", " ".join(str(part) for part in command), flush=True)
    return subprocess.run(command, check=True, cwd=REPO, **kwargs)


def output(command):
    return subprocess.check_output(command, cwd=REPO, text=True, stderr=subprocess.STDOUT).strip()


def mysql(sql):
    return output(["mysql", "-uroot", "-N", "-e", sql])


def prepare_system():
    if not WORKING.is_dir() or getattr(os, "geteuid", lambda: -1)() != 0 or platform.system() != "Linux":
        raise RuntimeError("Only run this script as root inside a Kaggle Linux CPU notebook")
    run(["apt-get", "update", "-qq"])
    run(["apt-get", "install", "-y", "mysql-server", "openjdk-21-jdk-headless"],
        env={**os.environ, "DEBIAN_FRONTEND": "noninteractive"})
    java_home = Path("/usr/lib/jvm/java-21-openjdk-amd64")
    if not (java_home / "bin/java").exists():
        raise RuntimeError("JDK 21 not installed at expected Ubuntu path; inspect java -version")
    os.environ["JAVA_HOME"] = str(java_home)
    os.environ["PATH"] = f"{java_home / 'bin'}:{os.environ['PATH']}"
    run([sys.executable, "-m", "pip", "install", "locust==2.32.1"])
    run(["service", "mysql", "start"])


def prepare_mysql():
    version = mysql("SELECT VERSION()")
    if not version.startswith("8."):
        raise RuntimeError(f"Expected MySQL 8.x, found {version}")
    database_password = secrets.token_urlsafe(24)
    mysql("CREATE DATABASE IF NOT EXISTS single_store_team CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
    existing = int(mysql("SELECT COUNT(*) FROM information_schema.tables "
                         "WHERE table_schema='single_store_team'"))
    if existing:
        raise RuntimeError("single_store_team is not empty; use a fresh Kaggle session")
    for host in ("localhost", "127.0.0.1"):
        mysql(f"CREATE USER IF NOT EXISTS 'load_user'@'{host}' IDENTIFIED BY '{database_password}'; "
              f"GRANT ALL PRIVILEGES ON single_store_team.* TO 'load_user'@'{host}'")
    return database_password, version


def start_api(run_dir, database_password):
    run(["bash", "./mvnw", "-B", "-Dmaven.test.skip=true", "package"])
    jars = [item for item in (REPO / "target").glob("single-store-service-*.jar")
            if not item.name.endswith(".original")]
    if len(jars) != 1:
        raise RuntimeError(f"Expected one Spring Boot JAR, found {jars}")
    admin_email = f"load-admin-{secrets.token_hex(5)}@example.com"
    admin_password = "Aa1!" + secrets.token_urlsafe(16)
    jwt_secret = base64.b64encode(secrets.token_bytes(48)).decode("ascii")
    env = {**os.environ,
           "SPRING_PROFILES_ACTIVE": "mysql", "SERVER_PORT": "18080",
           "DB_URL": "jdbc:mysql://127.0.0.1:3306/single_store_team?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true",
           "DB_USERNAME": "load_user", "DB_PASSWORD": database_password,
           "SPRING_JPA_HIBERNATE_DDL_AUTO": "validate",
           "BOOTSTRAP_ADMIN_EMAIL": admin_email,
           "BOOTSTRAP_ADMIN_PASSWORD": admin_password,
           "JWT_SECRET": jwt_secret}
    api_log = (run_dir / "api.log").open("w", encoding="utf-8")
    process = subprocess.Popen([str(Path(os.environ["JAVA_HOME"]) / "bin/java"), "-jar", str(jars[0])],
                               cwd=REPO, env=env, stdout=api_log, stderr=subprocess.STDOUT)
    try:
        for _ in range(90):
            if process.poll() is not None:
                raise RuntimeError(f"API exited early; inspect {run_dir / 'api.log'}")
            try:
                with urllib.request.urlopen(HOST + "/api/products", timeout=2) as response:
                    if response.status == 200:
                        break
            except (urllib.error.URLError, TimeoutError):
                pass
            time.sleep(2)
        else:
            raise RuntimeError(f"API not ready after 180 seconds; inspect {run_dir / 'api.log'}")
        versions = mysql("SELECT GROUP_CONCAT(version ORDER BY installed_rank) "
                         "FROM single_store_team.flyway_schema_history WHERE success=1")
        if versions != "1,2":
            raise RuntimeError(f"Unexpected Flyway versions: {versions}")
        tables = int(mysql("SELECT COUNT(*) FROM information_schema.tables "
                           "WHERE table_schema='single_store_team' AND table_name IN "
                           "('users','products','orders','order_items','cart_items','inventory_movements')"))
        if tables != 6:
            raise RuntimeError(f"Expected six business tables, got {tables}")
        return process, api_log, admin_email, admin_password
    except Exception:
        process.terminate()
        process.wait(timeout=15)
        api_log.close()
        raise


def memory_gb():
    for line in Path("/proc/meminfo").read_text().splitlines():
        if line.startswith("MemTotal:"):
            return round(int(line.split()[1]) / 1024 / 1024, 2)
    return "unknown"


def api_post(path, body, expected=200, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(HOST + path, data=json.dumps(body).encode("utf-8"),
                                     headers=headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            if response.status != expected:
                raise RuntimeError(f"Setup {path}: expected {expected}, got {response.status}")
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"Setup {path} returned {error.code}: "
                           f"{error.read(300).decode('utf-8', errors='replace')}") from error


def prepare_fixtures(users, admin_token, public_product_ids):
    fixtures = []
    for _ in range(users):
        suffix = uuid.uuid4().hex[:16]
        email = f"load-{suffix}@example.com"
        password = "LoadCustomer@123"
        api_post("/api/auth/register", {
            "fullName": "Load test customer", "email": email, "password": password,
        }, expected=201)
        token = api_post("/api/auth/login", {
            "email": email, "password": password,
        })["accessToken"]
        product = api_post("/api/products", {
            "sku": f"LOAD-{suffix}", "name": "Sản phẩm tải riêng",
            "description": "Chỉ dùng trong DB kiểm thử tải",
            "price": 1000, "stockQuantity": 100000,
        }, expected=201, token=admin_token)
        fixtures.append({"email": email, "password": password, "token": token,
                         "product_id": product["id"], "public_product_ids": public_product_ids})
    return fixtures


def run_phases(run_dir, metadata, admin_email, admin_password):
    admin_token = api_post("/api/auth/login", {
        "email": admin_email, "password": admin_password,
    })["accessToken"]
    with urllib.request.urlopen(HOST + "/api/products?page=0&size=20", timeout=30) as response:
        public_product_ids = [item["id"] for item in json.load(response)["items"]]
    if not public_product_ids:
        raise RuntimeError("No products available for detail GET task")
    only_smoke = os.environ.get("LOAD_SMOKE_ONLY") == "1"
    for phase, users, seconds in PHASES:
        if only_smoke and phase != "smoke":
            break
        fixtures = prepare_fixtures(users, admin_token, public_product_ids)
        prefix = run_dir / phase
        command = [sys.executable, "-m", "locust", "-f", str(REPO / "performance/locustfile.py"),
                   "--headless", "--host", HOST, "-u", str(users), "-r", "5",
                   "-t", f"{seconds}s", "--csv", str(prefix), "--csv-full-history",
                   "--html", str(run_dir / f"{phase}_report.html")]
        print(f"Starting {phase}: {users} users for {seconds}s", flush=True)
        fixture_path = None
        try:
            with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", suffix=".json",
                                             prefix="single-store-load-fixtures-", delete=False) as fixture_file:
                json.dump(fixtures, fixture_file)
                fixture_path = Path(fixture_file.name)
            env = {**os.environ, "LOAD_FIXTURES_PATH": str(fixture_path)}
            with (run_dir / f"{phase}_locust.log").open("w", encoding="utf-8") as log:
                finished = subprocess.run(command, cwd=REPO, env=env, stdout=log,
                                          stderr=subprocess.STDOUT, check=False, timeout=seconds + 120)
        finally:
            if fixture_path is not None:
                fixture_path.unlink(missing_ok=True)
        metadata["phases"][phase] = {
            "users": users, "duration_seconds": seconds, "exit_code": finished.returncode}
        (run_dir / "metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
        build_report(run_dir)
        if finished.returncode:
            raise RuntimeError(f"Locust {phase} failed; inspect {phase}_locust.log and REPORT.md")
        if phase == "main":
            main_result = next(item for item in build_report(run_dir) if item["phase"] == "main")
            if main_result["failures"]:
                metadata["high_skipped_reason"] = "Main phase had failed requests"
                (run_dir / "metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
                break


def main():
    if not WORKING.is_dir() or getattr(os, "geteuid", lambda: -1)() != 0 or platform.system() != "Linux":
        raise RuntimeError("Only run this script as root inside a Kaggle Linux CPU notebook")
    if output(["git", "status", "--porcelain"]):
        raise RuntimeError("Commit source changes before benchmarking; use a clean Git checkout")
    run_dir = WORKING / "single_store_load" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    run_dir.mkdir(parents=True, exist_ok=False)
    print(f"Evidence directory: {run_dir}", flush=True)
    process = None
    api_log = None
    try:
        prepare_system()
        database_password, mysql_version = prepare_mysql()
        process, api_log, admin_email, admin_password = start_api(run_dir, database_password)
        metadata = {
            "started_at_utc": datetime.now(timezone.utc).isoformat(),
            "environment": "Kaggle CPU Notebook", "git_commit": output(["git", "rev-parse", "HEAD"]),
            "cpu_count": os.cpu_count(), "memory_gb": memory_gb(),
            "java_version": output(["java", "-version"]).splitlines()[0],
            "mysql_version": mysql_version,
            "locust_version": output([sys.executable, "-m", "locust", "--version"]),
            "phases": {},
        }
        (run_dir / "metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
        run_phases(run_dir, metadata, admin_email, admin_password)
        print(f"Finished. Download {run_dir} (CSV, HTML, logs, REPORT.md).", flush=True)
    finally:
        if process is not None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        if api_log is not None:
            api_log.close()


if __name__ == "__main__":
    main()
