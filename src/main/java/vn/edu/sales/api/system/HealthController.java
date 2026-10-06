package vn.edu.sales.api.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Process readiness only; database readiness is added with the persistence module. */
@RestController
public class HealthController {
    @GetMapping("/api/health")
    public HealthResponse health() {
        return new HealthResponse("single-store-service", "UP", "base");
    }

    public record HealthResponse(String service, String status, String stage) {}
}
