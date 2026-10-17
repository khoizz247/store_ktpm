"""Verify MySQL readiness; optional order/cart smoke tests persist demo data."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal
import json
from pathlib import Path
import secrets
import subprocess
from threading import Barrier
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


def exercise_cart(base_url):
    row = sql("SELECT id,price,stock_quantity FROM products WHERE status='ACTIVE' AND stock_quantity>=2 ORDER BY id LIMIT 1")
    check(bool(row), "No active product with at least two units for cart smoke.")
    product_id, price, before = row.split("\t")
    product_id, before = int(product_id), int(before)
    email = "d5-" + uuid.uuid4().hex + "@example.test"
    password = "Demo@" + secrets.token_hex(16)
    status, registered = request(base_url, "/api/auth/register", "POST", {
        "fullName": "D5 cart customer", "email": email, "password": password})
    check(status == 201 and registered["role"] == "CUSTOMER", "Cart customer registration failed.")
    status, logged = request(base_url, "/api/auth/login", "POST", {"email": email, "password": password})
    check(status == 200 and bool(logged.get("accessToken")), "Cart customer login failed.")
    token = logged["accessToken"]
    status, lines = request(base_url, f"/api/cart/items/{product_id}", "PUT", {"quantity": 2}, token)
    check(status == 200 and len(lines) == 1 and lines[0]["quantity"] == 2, "Cart PUT failed.")
    status, product = request(base_url, f"/api/products/{product_id}")
    check(status == 200 and product["stockQuantity"] == before, "Adding to cart must not reserve stock.")
    status, created = request(base_url, "/api/cart/checkout", "POST", {
        "recipientName": "D5 smoke", "recipientPhone": "0900000000", "shippingAddress": "Demo only"}, token)
    check(status == 201 and created["status"] == "PENDING", "Cart checkout failed.")
    check(Decimal(str(created["totalAmount"])) == Decimal(price) * 2, "Wrong cart order total.")
    status, lines = request(base_url, "/api/cart", token=token)
    check(status == 200 and lines == [], "Successful checkout must clear the cart.")
    status, product = request(base_url, f"/api/products/{product_id}")
    check(status == 200 and product["stockQuantity"] == before - 2, "Checkout must decrease stock by two.")
    order_id = int(created["id"])
    check(sql(f"SELECT COUNT(*) FROM inventory_movements WHERE order_id={order_id} AND movement_type='SALE' AND quantity_change=-2") == "1",
          "Expected one SALE audit for cart checkout.")
    print(f"PASS cart smoke: order {order_id}, stock {before} -> {before - 2}, cart empty.")
    print("Demo customer/order/audit remain in MySQL; stock decreased by two, no automatic cancellation.")


def competing_checkouts(base_url, token, body):
    """Start two real HTTP requests together; database assertions are checked separately."""
    start = Barrier(2)

    def checkout():
        start.wait(timeout=10)
        return request(base_url, "/api/cart/checkout", "POST", body, token)

    with ThreadPoolExecutor(max_workers=2) as executor:
        futures = [executor.submit(checkout) for _ in range(2)]
        return [future.result(timeout=45) for future in futures]


def check_competing_cart_result(base_url, token, user_id, product_id, before, price, replies):
    statuses = sorted(status for status, _ in replies)
    check(statuses == [201, 409], f"Expected one checkout 201 and one conflict 409, got {statuses}.")
    created = next(body for status, body in replies if status == 201)
    check(created["status"] == "PENDING" and Decimal(str(created["totalAmount"])) == Decimal(price) * 2,
          "Wrong order status/total in competing cart checkout.")
    order_id = int(created["id"])
    check(sql(f"SELECT COUNT(*) FROM orders WHERE user_id={user_id}") == "1",
          "Competing checkouts must create exactly one order for this new customer.")
    check(sql(f"SELECT COUNT(*) FROM order_items WHERE order_id={order_id}") == "1",
          "Expected exactly one order item.")
    check(int(sql(f"SELECT stock_quantity FROM products WHERE id={product_id}")) == before - 2,
          "Competing checkouts deducted stock twice or another buyer changed the stock.")
    check(sql(f"SELECT COUNT(*) FROM inventory_movements WHERE order_id={order_id}") == "1",
          "Expected exactly one audit for the checkout order.")
    check(sql(f"SELECT COUNT(*) FROM inventory_movements WHERE order_id={order_id} AND product_id={product_id} "
              f"AND movement_type='SALE' AND quantity_change=-2 AND stock_before={before} "
              f"AND stock_after={before - 2} AND created_by_user_id={user_id}") == "1",
          "Wrong audit stock equation or actor for competing checkout.")
    status, lines = request(base_url, "/api/cart", token=token)
    check(status == 200 and lines == [], "Winning checkout did not clear the cart.")
    return order_id


def exercise_cart_concurrently(base_url):
    row = sql("SELECT id,price,stock_quantity FROM products WHERE status='ACTIVE' AND stock_quantity>=2 ORDER BY id LIMIT 1")
    check(bool(row), "No active product with two units for competing cart smoke.")
    product_id, price, before = row.split("\t")
    product_id, before = int(product_id), int(before)
    email = "d6-" + uuid.uuid4().hex + "@example.test"
    password = "Demo@" + secrets.token_hex(16)
    status, registered = request(base_url, "/api/auth/register", "POST", {
        "fullName": "D6 competing customer", "email": email, "password": password})
    check(status == 201 and registered["role"] == "CUSTOMER", "D6 customer registration failed.")
    token = registered["accessToken"]
    user_id = int(sql(f"SELECT id FROM users WHERE email='{email}'"))
    status, lines = request(base_url, f"/api/cart/items/{product_id}", "PUT", {"quantity": 2}, token)
    check(status == 200 and len(lines) == 1 and lines[0]["quantity"] == 2, "D6 cart setup failed.")
    check(int(sql(f"SELECT stock_quantity FROM products WHERE id={product_id}")) == before,
          "Cart setup changed stock or another buyer is active.")
    replies = competing_checkouts(base_url, token, {
        "recipientName": "D6 smoke", "recipientPhone": "0900000000", "shippingAddress": "Demo only"})
    order_id = check_competing_cart_result(base_url, token, user_id, product_id, before, price, replies)
    print(f"PASS competing cart: HTTP 201/409, one order {order_id}, stock {before} -> {before - 2}, one SALE, cart empty.")
    print("Demo customer/order/audit remain in MySQL; no automatic cancellation. This is not a load test.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:18081")
    exercises = parser.add_mutually_exclusive_group()
    exercises.add_argument("--exercise-orders", action="store_true", help="Creates demo customers and an order; stock decreases by one.")
    exercises.add_argument("--exercise-cart", action="store_true", help="Creates a demo customer and checks out two units; stock decreases by two.")
    exercises.add_argument("--exercise-cart-concurrently", action="store_true",
                           help="Two checkout requests for one demo cart; persists one order and decreases stock by two.")
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
    elif args.exercise_cart:
        exercise_cart(args.base_url)
    elif args.exercise_cart_concurrently:
        exercise_cart_concurrently(args.base_url)


if __name__ == "__main__":
    main()
