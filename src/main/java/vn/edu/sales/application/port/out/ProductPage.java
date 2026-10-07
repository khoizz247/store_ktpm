package vn.edu.sales.application.port.out;

import vn.edu.sales.domain.model.Product;

import java.util.List;

public record ProductPage(List<Product> items, int page, int size, long totalElements, int totalPages) {
}
