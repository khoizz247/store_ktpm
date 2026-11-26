package vn.edu.sales.application.services;

import vn.edu.sales.domain.exception.TooManyAttemptsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chặn tạm thời một email sau {@code maxFailures} lần đăng nhập sai liên tiếp trong {@code window}.
 * Lưu trong bộ nhớ của một instance: đủ cho một cửa hàng chạy một tiến trình;
 * nếu chạy nhiều instance cần chuyển sang kho dùng chung (Redis, DB).
 */
public class LoginAttemptLimiter {
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(int maxFailures, Duration window, Clock clock) {
        if (maxFailures < 1) throw new IllegalArgumentException("maxFailures phải >= 1");
        if (window == null || window.isNegative() || window.isZero())
            throw new IllegalArgumentException("window phải dương");
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /** Ném lỗi nếu key đang bị chặn; gọi trước khi kiểm tra mật khẩu. */
    public void checkAllowed(String key) {
        Instant now = clock.instant();
        Attempts current = attempts.get(key);
        if (current == null) return;
        if (current.isExpired(now, window)) {
            attempts.remove(key, current);
            return;
        }
        if (current.failures() >= maxFailures) {
            long retryAfter = Math.max(1, Duration.between(now, current.firstFailure().plus(window)).toSeconds());
            throw new TooManyAttemptsException(
                    "Đăng nhập sai quá nhiều lần; vui lòng thử lại sau " + retryAfter + " giây", retryAfter);
        }
    }

    public void recordFailure(String key) {
        Instant now = clock.instant();
        if (attempts.size() >= MAX_TRACKED_KEYS) evictExpired(now);
        attempts.compute(key, (ignored, current) ->
                current == null || current.isExpired(now, window)
                        ? new Attempts(1, now)
                        : new Attempts(current.failures() + 1, current.firstFailure()));
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private void evictExpired(Instant now) {
        attempts.entrySet().removeIf(entry -> entry.getValue().isExpired(now, window));
        // Nếu vẫn đầy (bị dò hàng loạt email), bỏ bớt để bộ nhớ không tăng vô hạn.
        if (attempts.size() >= MAX_TRACKED_KEYS) attempts.clear();
    }

    private record Attempts(int failures, Instant firstFailure) {
        boolean isExpired(Instant now, Duration window) {
            return !now.isBefore(firstFailure.plus(window));
        }
    }
}
