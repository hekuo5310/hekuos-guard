package dev.hekuo.guard.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenBucketTest {
    @Test void refillsAtConfiguredRate() {
        TokenBucket bucket = new TokenBucket(2, 2, 0);
        assertTrue(bucket.tryTake(0));
        assertTrue(bucket.tryTake(0));
        assertFalse(bucket.tryTake(0));
        assertTrue(bucket.tryTake(500_000_000L));
        assertFalse(bucket.tryTake(500_000_000L));
    }
}
