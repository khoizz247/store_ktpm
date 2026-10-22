"""Static HTTP-demo regressions, NOT live API/IntelliJ execution tests."""
import json
from pathlib import Path
import re
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
DEMOS = (DOCS / "order-demo.http", DOCS / "checkout-demo.http")
REQUEST = re.compile(r"^(GET|POST|PUT|PATCH|DELETE) (.+)$", re.MULTILINE)
VARIABLE = re.compile(r"\{\{([A-Za-z][A-Za-z0-9]*)\}\}")


def requests(path):
    for block in re.split(r"^###.*$", path.read_text(encoding="utf-8"), flags=re.MULTILINE):
        match = REQUEST.search(block)
        if match:
            yield match.group(1), match.group(2), block


class HttpDemoContractTest(unittest.TestCase):
    def test_public_environment_has_only_h2_demo_credentials(self):
        environments = json.loads((DOCS / "http-client.env.json").read_text(encoding="utf-8"))
        self.assertEqual(set(environments), {"h2"})
        self.assertEqual(environments["h2"]["base"], "http://localhost:18080")
        self.assertEqual(environments["h2"]["adminEmail"], "admin@example.com")
        self.assertEqual(environments["h2"]["adminPassword"], "Admin@123")

    def test_private_environment_and_dotenv_are_git_ignored(self):
        for path in ("docs/http-client.private.env.json", ".env"):
            result = subprocess.run(["git", "check-ignore", "--no-index", "--", path],
                                    cwd=ROOT, capture_output=True, text=True, check=False)
            self.assertEqual(result.returncode, 0, path)
            tracked = subprocess.check_output(["git", "ls-files", "--", path], cwd=ROOT, text=True)
            self.assertFalse(tracked.strip(), path)

    def test_docs_have_no_case_insensitive_filename_collision(self):
        names = [p.relative_to(DOCS).as_posix().casefold() for p in DOCS.rglob("*") if p.is_file()]
        self.assertEqual(len(names), len(set(names)))

    def test_every_request_has_a_status_assertion(self):
        for path in DEMOS:
            blocks = list(requests(path))
            self.assertGreater(len(blocks), 10)
            for method, url, block in blocks:
                with self.subTest(file=path.name, method=method, url=url):
                    self.assertIn("> {%", block)
                    self.assertRegex(block, r"client\.assert\(response\.status === [1-5][0-9]{2}\)")

    def test_json_request_bodies_are_valid_after_variable_substitution(self):
        for path in DEMOS:
            for method, url, block in requests(path):
                if "Content-Type: application/json" not in block:
                    continue
                body = block.split("Content-Type: application/json", 1)[1].split("> {%", 1)[0].strip()
                with self.subTest(file=path.name, method=method, url=url):
                    json.loads(VARIABLE.sub("1", body))

    def test_variables_are_defined_before_request_uses_them(self):
        environment = json.loads((DOCS / "http-client.env.json").read_text(encoding="utf-8"))["h2"]
        for path in DEMOS:
            content = path.read_text(encoding="utf-8")
            available = set(environment) | set(re.findall(r"^@(\w+)\s*=", content, re.MULTILINE))
            for method, url, block in requests(path):
                request_text = block.split("> {%", 1)[0]
                with self.subTest(file=path.name, method=method, url=url):
                    self.assertFalse(set(VARIABLE.findall(request_text)) - available)
                available.update(re.findall(r'client\.global\.set\("(\w+)"', block))

    def test_registration_examples_never_claim_to_create_admin(self):
        for path in DEMOS:
            content = path.read_text(encoding="utf-8")
            self.assertNotIn("Register Admin", content)
            self.assertNotRegex(content, r"Bearer (ADMIN_TOKEN|CUSTOMER_[AB]_TOKEN)")
            for method, url, block in requests(path):
                if method == "POST" and url.endswith("/api/auth/register"):
                    self.assertNotIn("{{adminEmail}}", block)
                    self.assertIn('response.body.role === "CUSTOMER"', block)
                if method == "POST" and url.endswith("/api/auth/login") and "{{adminEmail}}" in block:
                    self.assertIn('response.body.role === "ADMIN"', block)


if __name__ == "__main__":
    unittest.main()
