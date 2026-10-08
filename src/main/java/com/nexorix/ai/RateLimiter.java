package com.nexorix.ai;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite simple: maximo N usos por clave (persona o IP) en una ventana de tiempo.
 * Se limpia solo para no crecer sin control con muchas claves distintas.
 */
public class RateLimiter {

    private static final int SWEEP_EVERY = 5_000;

    private final int max;
    private final long windowMillis;
    private final Map<String, Deque<Long>> uses = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();

    public RateLimiter(int max, Duration window) {
        this.max = max;
        this.windowMillis = window.toMillis();
    }

    /** true si todavia puede usarlo (y lo cuenta). */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        if (calls.incrementAndGet() % SWEEP_EVERY == 0) {
            sweep(now);
        }
        Deque<Long> queue = uses.computeIfAbsent(key == null ? "" : key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() < now - windowMillis) {
                queue.pollFirst();
            }
            if (queue.size() >= max) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }

    /** Segundos hasta que la clave pueda volver a usarlo (aproximado). */
    public long secondsUntilAvailable(String key) {
        Deque<Long> queue = uses.get(key);
        if (queue == null) return 0;
        synchronized (queue) {
            Long oldest = queue.peekFirst();
            return oldest == null ? 0 : Math.max(1, (oldest + windowMillis - System.currentTimeMillis()) / 1000);
        }
    }

    private void sweep(long now) {
        uses.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                Long last = entry.getValue().peekLast();
                return last == null || last < now - windowMillis;
            }
        });
    }

    int size() {
        return uses.size();
    }
}
