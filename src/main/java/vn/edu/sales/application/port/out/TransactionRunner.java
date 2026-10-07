package vn.edu.sales.application.port.out;

import java.util.function.Supplier;

public interface TransactionRunner {
    <T> T execute(Supplier<T> work);
}
