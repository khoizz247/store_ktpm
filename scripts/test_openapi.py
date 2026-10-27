"""Offline documentation contract checks; no server, database or network calls."""
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
JAVA = ROOT / "src/main/java"
METHODS = {"get", "post", "put", "patch", "delete"}
SPEC = json.loads((DOCS / "openapi.json").read_text(encoding="utf-8"))


def operations():
    return [(path, method, operation)
            for path, item in SPEC["paths"].items()
            for method, operation in item.items() if method in METHODS]


def walk(value):
    yield value
    if isinstance(value, dict):
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


class OpenApiContractTest(unittest.TestCase):
    def test_version_and_local_servers(self):
        self.assertEqual(SPEC["openapi"], "3.0.3")
        self.assertEqual({s["url"] for s in SPEC["servers"]},
                         {"http://localhost:18080", "http://localhost:18081"})
        self.assertEqual(SPEC["components"]["securitySchemes"]["bearerAuth"]["type"], "http")

    def test_operations_match_all_production_controllers(self):
        expected = {}
        codes = {"CREATED": "201", "NO_CONTENT": "204"}
        for source in JAVA.rglob("*Controller.java"):
            text = source.read_text(encoding="utf-8")
            base = re.search(r'@RequestMapping\("([^"]+)"\)', text)
            prefix = base.group(1) if base else ""
            for match in re.finditer(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\("([^"]*)"\))?', text):
                method, suffix = match.group(1).lower(), match.group(2) or ""
                # Find the annotations before this mapped method declaration.
                tail = text[match.end():]
                declaration = re.search(r"\bpublic\s+", tail)
                self.assertIsNotNone(declaration, source.name)
                annotations = tail[:declaration.start()]
                status = re.search(r"@ResponseStatus\(HttpStatus\.(\w+)\)", annotations)
                expected[(prefix + suffix, method)] = codes.get(status.group(1), "200") if status else "200"
        actual = {(p, m): op for p, m, op in operations()}
        self.assertEqual(set(actual), set(expected))
        self.assertEqual(len(actual), 28)
        for key, status in expected.items():
            self.assertIn(status, actual[key]["responses"], key)

    def test_all_references_resolve_locally(self):
        for node in walk(SPEC):
            if not isinstance(node, dict) or "$ref" not in node:
                continue
            ref = node["$ref"]
            self.assertTrue(ref.startswith("#/"), ref)
            target = SPEC
            for segment in ref[2:].split("/"):
                target = target[segment.replace("~1", "/").replace("~0", "~")]

    def test_path_parameters_and_operation_ids(self):
        ids = []
        parameters = SPEC["components"]["parameters"]
        for path, method, op in operations():
            ids.append(op["operationId"])
            params = [parameters[p["$ref"].rsplit("/", 1)[1]] if "$ref" in p else p
                      for p in op.get("parameters", [])]
            declared = {p["name"] for p in params if p["in"] == "path" and p.get("required")}
            self.assertEqual(declared, set(re.findall(r"\{([^}]+)\}", path)), (method, path))
            self.assertTrue(any(str(s).startswith("2") for s in op["responses"]))
            for status, response in op["responses"].items():
                if status == "204":
                    self.assertNotIn("content", response)
        self.assertEqual(len(ids), len(set(ids)))

    def test_roles_match_security_config(self):
        for path, method, op in operations():
            public = path.startswith("/api/auth/") or path == "/api/health" or (
                method == "get" and path.startswith("/api/products"))
            if public:
                self.assertEqual(op["security"], [])
                self.assertEqual(op["x-roles"], [])
            else:
                self.assertEqual(op["security"], [{"bearerAuth": []}])
                role = (["CUSTOMER", "ADMIN"] if path.startswith("/api/users/")
                        else ["ADMIN"] if path.startswith(("/api/admin/", "/api/products"))
                        else ["CUSTOMER"])
                self.assertEqual(op["x-roles"], role, (method, path))
                self.assertIn("401", op["responses"])
                self.assertIn("403", op["responses"])

    def test_enums_match_source(self):
        for name in ("Role", "ProductStatus", "OrderStatus"):
            path = JAVA / "vn/edu/sales/domain/model" / (name + ".java")
            body = path.read_text(encoding="utf-8").split("{", 1)[1].split("}", 1)[0]
            self.assertEqual(SPEC["components"]["schemas"][name]["enum"],
                             re.findall(r"\b[A-Z][A-Z_]+\b", body))
        self.assertNotIn("/api/user/me", SPEC["paths"])

    def test_request_schemas_and_examples(self):
        schemas = SPEC["components"]["schemas"]
        for path, method, op in operations():
            body = op.get("requestBody")
            if not body:
                continue
            self.assertTrue(body["required"])
            media = body["content"]["application/json"]
            schema = schemas[media["schema"]["$ref"].rsplit("/", 1)[1]]
            sample = media["example"]
            self.assertTrue(set(schema["required"]).issubset(sample), (method, path))
            self.assertTrue(set(sample).issubset(schema["properties"]), (method, path))
        self.assertEqual(schemas["QuantityRequest"]["properties"]["quantity"]["minimum"], 1)
        self.assertEqual(schemas["CreateOrderRequest"]["properties"]["items"]["minItems"], 1)
        self.assertEqual(SPEC["components"]["parameters"]["Size"]["schema"]["maximum"], 100)

    def test_preview_is_read_only(self):
        html = (DOCS / "api-preview.html").read_text(encoding="utf-8")
        self.assertIn("supportedSubmitMethods: []", html)
        self.assertIn("validatorUrl: null", html)
        self.assertIn("persistAuthorization: false", html)
        self.assertNotIn("petstore", html.lower())


if __name__ == "__main__":
    unittest.main()
