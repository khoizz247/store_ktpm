"""Unit tests for smoke-test orchestration; these do NOT connect to Docker/MySQL."""
from contextlib import redirect_stdout
import io
import unittest
from unittest.mock import patch

import verify_mysql as smoke


class VerifyMysqlScriptTest(unittest.TestCase):
    def run_readiness(self, query_results):
        with patch.object(smoke, "sql", side_effect=query_results), \
                patch.object(smoke, "request", side_effect=[(200, {"status": "UP"}), (200, {})]), \
                patch("sys.argv", ["verify_mysql.py"]), redirect_stdout(io.StringIO()):
            smoke.main()

    def test_readiness_passes_without_exercising_orders(self):
        with patch.object(smoke, "exercise_orders") as exercise:
            self.run_readiness(["single_store_team", "\n".join(smoke.EXPECTED_TABLES | {"flyway_schema_history"}),
                                "1\t1\n2\t1", "0"])
            exercise.assert_not_called()

    def test_wrong_database_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "Wrong database"):
            self.run_readiness(["single_store"])

    def test_missing_business_table_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "Missing business tables"):
            self.run_readiness(["single_store_team", "users\nproducts"])

    def test_failed_migration_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "V1 and V2"):
            self.run_readiness(["single_store_team", "\n".join(smoke.EXPECTED_TABLES | {"flyway_schema_history"}),
                                "1\t1\n2\t0"])

    def test_order_smoke_checks_stock_audit_owner_and_isolation(self):
        replies = [(201, {"role": "CUSTOMER"}), (200, {"accessToken": "test-token"}), (401, None),
                   (201, {"id": 10, "status": "PENDING", "totalAmount": 120000}),
                   (200, {"items": [{"id": 10}]}), (200, {"id": 10}),
                   (201, {"accessToken": "stranger-token"}), (404, None)]
        with patch.object(smoke, "sql", side_effect=["3\t120000.00\t10", "9", "1"]), \
                patch.object(smoke, "request", side_effect=replies) as request, redirect_stdout(io.StringIO()):
            smoke.exercise_orders("http://localhost:18081")
            self.assertEqual(request.call_count, 8)
            self.assertEqual(request.call_args.kwargs["token"], "stranger-token")

    def test_wrong_order_total_fails_before_success_is_reported(self):
        replies = [(201, {"role": "CUSTOMER"}), (200, {"accessToken": "test-token"}), (401, None),
                   (201, {"id": 10, "status": "PENDING", "totalAmount": 1})]
        with patch.object(smoke, "sql", return_value="3\t120000.00\t10"), \
                patch.object(smoke, "request", side_effect=replies), redirect_stdout(io.StringIO()):
            with self.assertRaisesRegex(RuntimeError, "Wrong server-calculated total"):
                smoke.exercise_orders("http://localhost:18081")


if __name__ == "__main__":
    unittest.main()
