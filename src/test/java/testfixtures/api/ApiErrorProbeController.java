package testfixtures.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.*;
import vn.edu.sales.domain.exception.BusinessConflictException;
import vn.edu.sales.domain.exception.InvalidCredentialsException;
import vn.edu.sales.domain.exception.ResourceNotFoundException;

// Test-only, outside vn.edu.sales so SpringBootApplication will not component-scan it.
// Only ApiExceptionHandlerStandaloneTest registers this fixture explicitly.
@RestController
@RequestMapping("/__test-errors")
public class ApiErrorProbeController {
    public static final String INTERNAL = "TEST-ONLY jdbc:mysql://private-host/test password=DO_NOT_LEAK SELECT secret_column";

    @GetMapping("/{kind}")
    public Object fail(@PathVariable("kind") String kind) {
        throw switch (kind) {
            case "not-found" -> new ResourceNotFoundException("Missing test resource");
            case "business" -> new BusinessConflictException("Test business conflict");
            case "credentials" -> new InvalidCredentialsException("Test invalid credentials");
            case "bad-request" -> new IllegalArgumentException("Test invalid argument");
            case "integrity" -> new DataIntegrityViolationException(INTERNAL);
            case "database" -> new DataAccessResourceFailureException(INTERNAL);
            case "transaction-create" -> new CannotCreateTransactionException(INTERNAL);
            case "transaction-system" -> new TransactionSystemException(INTERNAL);
            case "pessimistic" -> new CannotAcquireLockException(INTERNAL);
            case "optimistic" -> new ObjectOptimisticLockingFailureException("TestResource", 7L);
            default -> new IllegalArgumentException("Unknown test case");
        };
    }

    @PostMapping("/body")
    public BodyRequest validate(@Valid @RequestBody BodyRequest body) { return body; }

    @GetMapping("/number")
    public int number(@RequestParam("value") int value) { return value; }

    public record BodyRequest(@NotBlank String name) {}
}
