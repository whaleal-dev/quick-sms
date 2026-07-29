package com.whaleal.ark.cloud.third.sms.guard;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简易滑动窗口限流（按手机号 / 全局 key）。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public class RateLimiter {

    private final int maxPerWindow;
    private final long windowMs;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int maxPerWindow, long windowMs) {
        this.maxPerWindow = Math.max(1, maxPerWindow);
        this.windowMs = Math.max(1000L, windowMs);
    }

    public static RateLimiter perMinute(int maxPerMinute) {
        return new RateLimiter(maxPerMinute, 60_000L);
    }

    public boolean tryAcquire(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, old) -> {
            if (old == null || now - old.startMs >= windowMs) {
                return new Window(now);
            }
            return old;
        });
        return w.count.incrementAndGet() <= maxPerWindow;
    }

    private static final class Window {
        final long startMs;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long startMs) {
            this.startMs = startMs;
        }
    }
}
