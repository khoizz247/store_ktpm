"""Weighted 60/15/10/10/5 HTTP workload; all fixtures are prepared beforehand."""

import itertools
import json
import os
import random
from pathlib import Path

from locust import HttpUser, between, task


_FIXTURES = json.loads(Path(os.environ["LOAD_FIXTURES_PATH"]).read_text(encoding="utf-8"))
_NEXT_FIXTURE = itertools.count()


class StoreCustomer(HttpUser):
    wait_time = between(1, 3)

    def on_start(self):
        index = next(_NEXT_FIXTURE)
        if index >= len(_FIXTURES):
            raise RuntimeError("Not enough prepared users for Locust -u")
        fixture = _FIXTURES[index]
        self.email = fixture["email"]
        self.password = fixture["password"]
        self.token = fixture["token"]
        self.own_product_id = fixture["product_id"]
        self.public_product_ids = fixture["public_product_ids"]

    def auth_headers(self):
        return {"Authorization": f"Bearer {self.token}"}

    @task(60)
    def list_products(self):
        self.client.get("/api/products?page=0&size=20", name="GET /api/products")

    @task(15)
    def product_detail(self):
        product_id = random.choice(self.public_product_ids)
        self.client.get(f"/api/products/{product_id}", name="GET /api/products/{id}")

    @task(10)
    def login(self):
        with self.client.post("/api/auth/login", json={
            "email": self.email, "password": self.password,
        }, name="POST /api/auth/login", catch_response=True) as response:
            if response.status_code != 200:
                response.failure(f"login returned {response.status_code}")
            else:
                try:
                    self.token = response.json()["accessToken"]
                except (ValueError, KeyError):
                    response.failure("login response has no accessToken")

    @task(10)
    def my_orders(self):
        self.client.get("/api/orders/me?page=0&size=20", headers=self.auth_headers(),
                        name="GET /api/orders/me")

    @task(5)
    def create_order(self):
        body = {
            "recipientName": "Khách tải",
            "recipientPhone": "0901234567",
            "shippingAddress": "1 Đường kiểm thử, Hà Nội",
            "items": [{"productId": self.own_product_id, "quantity": 1}],
        }
        with self.client.post("/api/orders", json=body, headers=self.auth_headers(),
                              name="POST /api/orders", catch_response=True) as response:
            if response.status_code != 201:
                response.failure(f"create order returned {response.status_code}: {response.text[:200]}")
