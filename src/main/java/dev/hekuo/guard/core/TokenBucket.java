package dev.hekuo.guard.core;

/** Small deterministic rate limiter; timestamps are supplied by the caller for testability. */
public final class TokenBucket {
    private final double rate;
    private final double capacity;
    private double tokens;
    private long lastNanos;

    public TokenBucket(double rate, double capacity, long nowNanos) {
        this.rate = rate;
        this.capacity = capacity;
        this.tokens = capacity;
        this.lastNanos = nowNanos;
    }

    public boolean tryTake(long nowNanos) {
        double elapsed = Math.max(0, nowNanos - lastNanos) / 1_000_000_000.0;
        tokens = Math.min(capacity, tokens + elapsed * rate);
        lastNanos = nowNanos;
        if (tokens < 1) return false;
        tokens--;
        return true;
    }
}
