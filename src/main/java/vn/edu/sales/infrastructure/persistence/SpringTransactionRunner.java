package vn.edu.sales.infrastructure.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.sales.application.port.out.TransactionRunner;

import java.util.function.Supplier;

@Component
public class SpringTransactionRunner implements TransactionRunner {
    private final TransactionTemplate transactionTemplate;

    public SpringTransactionRunner(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
