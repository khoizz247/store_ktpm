package vn.edu.sales.api.system.common;

import java.time.Instant;
import java.util.Map;

public record ApiError(Instant timestamp, int status, String code, String message,
                       String path, Map<String, String> fields) {
    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, null);
    }

    public static ApiError of(int status, String code, String message, String path,
                              Map<String, String> fields) {
        return new ApiError(Instant.now(), status, code, message, path, fields);
    }
}
