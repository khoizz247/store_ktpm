"""Verify D4 against the project's MySQL. Optional order smoke test persists demo data."""
import argparse
from decimal import Decimal
import json
from pathlib import Path
import secrets
import subprocess
import urllib.error
import urllib.request
import uuid

PROJECT = Path(__file__).resolve().parents[1]
EXPECTED_TABLES = {"users", "products", "orders", "order_items", "cart_items", "inventory_movements"}


def check(condition, message):
    if not condition:
        raise RuntimeError(message)


def sql(query):
    # Credentials stay inside the container environment, not in command arguments/output.
    shell = ('MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --protocol=tcp -h127.0.0.1 '
             '-u"$MYSQL_USER" "$MYSQL_DATABASE" --batch --skip-column-names -e "$1"')
    result = subprocess.run(["docker", "compose", "exec", "-T", "mysql", "sh", "-c", shell, "sh", query],
                            cwd=PROJECT, text=True, capture_output=True, timeout=30)
    check(result.returncode == 0, "MySQL query failed; inspect docker compose logs mysql locally.")
    return result.stdout.strip()


def request(base_url, path, method="GET", body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(base_url.rstrip("/") + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        return error.code, None


def exercise_orders(base_url):
    row = sql("SELECT id,price,stock_quantity FROM products WHERE status='ACTIVE' AND stock_quantity>=1 ORDER BY id LIMIT 1")
    check(bool(row), "No active product with stock; order smoke test cannot run.")
    product_id, price, before = row.split("\t")
    product_id, before = int(product_id), int(before)
    email = "d4-" + uuid.uuid4().hex + "@example.test"
    password = "Demo@" + secrets.token_hex(16)
    status, registered = request(base_url, "/api/auth/register", "POST", {
        "fullName": "D4 smoke customer", "email": email, "password": password})
    check(status == 201 and registered["role"] == "CUSTOMER", "Customer registration failed.")
    status, logged = request(base_url, "/api/auth/login", "POST", {"email": email, "password": password})
    check(status == 200 and bool(logged.get("accessToken")), "Customer login failed.")
    token = logged["accessToken"]
    check(request(base_url, "/api/orders/me")[0] == 401, "Orders must require authentication.")
    status, created = request(base_url, "/api/orders", "POST", {
        "recipientName": "D4 smoke", "recipientPhone": "0900000000", "shippingAddress": "Demo only",
        "items": [{"productId": product_id, "quantity": 1}]}, token)
    check(status == 201, "Order creation failed.")
    check(created["status"] == "PENDING", "New order is not PENDING.")
    check(Decimal(str(created["totalAmount"])) == Decimal(price), "Wrong server-calculated total.")
    order_id = int(created["id"])
    check(int(sql(f"SELECT stock_quantity FROM products WHERE id={product_id}")) == before - 1,
          "Product stock did not decrease by one (run without competing buyers).")
    check(sql(f"SELECT COUNT(*) FROM inventory_movements WHERE order_id={order_id} AND movement_type='SALE' AND quantity_change=-1") == "1",
          "Expected one SALE audit record.")
    status, page = request(base_url, "/api/orders/me", token=token)
    check(status == 200 and any(int(order["id"]) == order_id for order in page["items"]), "Created order missing from own list.")
    status, detail = request(base_url, f"/api/orders/{order_id}", token=token)
    check(status == 200 and int(detail["id"]) == order_id, "Order detail failed.")
    status, stranger = request(base_url, "/api/auth/register", "POST", {
        "fullName": "D4 other customer", "email": "d4-" + uuid.uuid4().hex + "@example.test", "password": password})
    check(status == 201, "Second customer registration failed.")
    check(request(base_url, f"/api/orders/{order_id}", token=stranger["accessToken"])[0] == 404,
          "A different customer must not see this order.")
    print(f"PASS order smoke: order {order_id}, stock {before} -> {before - 1}.")
    print("Two demo customers and the order/audit remain in MySQL; no automatic deletion or cancellation was performed.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:18081")
    parser.add_argument("--exercise-orders", action="store_true", help="Creates a demo customer and order; stock decreases by one.")
    args = parser.parse_args()
    check(sql("SELECT DATABASE()") == "single_store_team", "Wrong database; refusing to continue.")
    tables = set(sql("SHOW TABLES").splitlines())
    check(EXPECTED_TABLES <= tables, "Missing business tables.")
    check("flyway_schema_history" in tables, "Missing Flyway schema history.")
    history = sql("SELECT version,success FROM flyway_schema_history WHERE version IN ('1','2') ORDER BY installed_rank")
    check(set(history.splitlines()) == {"1\t1", "2\t1"}, "V1 and V2 must both succeed.")
    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0") == "0", "Failed migration found.")
    status, health = request(args.base_url, "/api/health")
    check(status == 200 and health.get("status") == "UP", "API health failed.")
    check(request(args.base_url, "/v3/api-docs")[0] == 200, "OpenAPI is unavailable.")
    print("PASS readiness: single_store_team, six business tables, Flyway V1/V2, API health and OpenAPI.")
    if args.exercise_orders:
        exercise_orders(args.base_url)


if __name__ == "__main__":
    main()
