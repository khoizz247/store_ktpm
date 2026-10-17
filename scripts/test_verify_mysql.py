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

    def test_cart_smoke_checks_no_reservation_then_checkout_and_clear(self):
        replies = [(201, {"role": "CUSTOMER"}), (200, {"accessToken": "test-token"}),
                   (200, [{"quantity": 2}]), (200, {"stockQuantity": 10}),
                   (201, {"id": 12, "status": "PENDING", "totalAmount": 240000}),
                   (200, []), (200, {"stockQuantity": 8})]
        with patch.object(smoke, "sql", side_effect=["3\t120000.00\t10", "1"]), \
                patch.object(smoke, "request", side_effect=replies) as request, redirect_stdout(io.StringIO()):
            smoke.exercise_cart("http://localhost:18082")
            self.assertEqual(request.call_count, 7)

    def test_cart_smoke_rejects_stock_reserved_before_checkout(self):
        replies = [(201, {"role": "CUSTOMER"}), (200, {"accessToken": "test-token"}),
                   (200, [{"quantity": 2}]), (200, {"stockQuantity": 8})]
        with patch.object(smoke, "sql", return_value="3\t120000.00\t10"), \
                patch.object(smoke, "request", side_effect=replies), redirect_stdout(io.StringIO()):
            with self.assertRaisesRegex(RuntimeError, "must not reserve stock"):
                smoke.exercise_cart("http://localhost:18082")

    def test_competing_checkout_requests_both_reach_http_client(self):
        # Request ordering is deliberately unspecified; both workers use the same cart/token.
        with patch.object(smoke, "request", side_effect=[(201, {"id": 1}), (409, None)]) as request:
            replies = smoke.competing_checkouts("http://localhost:18082", "test-token", {"recipientName": "D6"})
            self.assertEqual(sorted(status for status, _ in replies), [201, 409])
            self.assertEqual(request.call_count, 2)
            for call in request.call_args_list:
                self.assertEqual(call.args[1:3], ("/api/cart/checkout", "POST"))
                self.assertEqual(call.args[4], "test-token")

    def test_competing_cart_verifies_single_order_stock_audit_and_empty_cart(self):
        replies = [(409, None), (201, {"id": 22, "status": "PENDING", "totalAmount": 240000})]
        with patch.object(smoke, "sql", side_effect=["1", "1", "8", "1", "1"]), \
                patch.object(smoke, "request", return_value=(200, [])):
            self.assertEqual(smoke.check_competing_cart_result(
                "http://localhost:18082", "test-token", 7, 3, 10, "120000.00", replies), 22)

    def test_competing_cart_rejects_double_success_and_unhandled_error(self):
        for replies in [[(201, {}), (201, {})], [(201, {}), (500, None)]]:
            with self.subTest(replies=replies), self.assertRaisesRegex(RuntimeError, "one checkout 201"):
                smoke.check_competing_cart_result("http://localhost:18082", "token", 7, 3, 10, "120000", replies)

    def test_competing_cart_rejects_double_stock_deduction(self):
        replies = [(201, {"id": 22, "status": "PENDING", "totalAmount": 240000}), (409, None)]
        with patch.object(smoke, "sql", side_effect=["1", "1", "6"]), \
                self.assertRaisesRegex(RuntimeError, "deducted stock twice"):
            smoke.check_competing_cart_result("http://localhost:18082", "token", 7, 3, 10, "120000", replies)


if __name__ == "__main__":
    unittest.main()
