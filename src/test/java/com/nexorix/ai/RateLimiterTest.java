package com.nexorix.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void permiteHastaElLimiteYLuegoBloquea() {
        RateLimiter limiter = new RateLimiter(3, Duration.ofHours(1));

        assertThat(limiter.tryAcquire("ana")).isTrue();
        assertThat(limiter.tryAcquire("ana")).isTrue();
        assertThat(limiter.tryAcquire("ana")).isTrue();
        assertThat(limiter.tryAcquire("ana")).isFalse();
        assertThat(limiter.secondsUntilAvailable("ana")).isPositive();
        // Cada persona tiene su propio limite.
        assertThat(limiter.tryAcquire("juan")).isTrue();
    }

    @Test
    void cuandoPasaLaVentanaSeVuelveAPermitir() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMillis(100));

        assertThat(limiter.tryAcquire("ana")).isTrue();
        assertThat(limiter.tryAcquire("ana")).isFalse();
        Thread.sleep(150);
        assertThat(limiter.tryAcquire("ana")).isTrue();
    }

    @Test
    void seLimpiaSoloConMuchasClaves() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(5, Duration.ofMillis(1));
        for (int i = 0; i < 4_000; i++) limiter.tryAcquire("ip-" + i);
        Thread.sleep(5);
        for (int i = 0; i < 1_100; i++) limiter.tryAcquire("otra");

        assertThat(limiter.size()).isLessThan(100);
    }
}
