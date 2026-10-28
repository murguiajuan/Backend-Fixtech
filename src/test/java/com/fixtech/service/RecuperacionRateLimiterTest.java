package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RecuperacionRateLimiterTest {

    @Test
    void permite_hasta_el_maximo_y_luego_bloquea() {
        RecuperacionRateLimiter limiter = new RecuperacionRateLimiter();

        assertTrue(limiter.permitir("ip:1", 2, Duration.ofMinutes(15)));
        assertTrue(limiter.permitir("ip:1", 2, Duration.ofMinutes(15)));
        assertFalse(limiter.permitir("ip:1", 2, Duration.ofMinutes(15)));
    }

    @Test
    void separa_los_limites_por_clave() {
        RecuperacionRateLimiter limiter = new RecuperacionRateLimiter();

        assertTrue(limiter.permitir("ip:1", 1, Duration.ofMinutes(15)));
        assertTrue(limiter.permitir("ip:2", 1, Duration.ofMinutes(15)));
        assertFalse(limiter.permitir("ip:1", 1, Duration.ofMinutes(15)));
    }

    @Test
    void limpiar_elimina_los_registros() {
        RecuperacionRateLimiter limiter = new RecuperacionRateLimiter();
        assertTrue(limiter.permitir("ip:1", 1, Duration.ofMinutes(15)));
        assertFalse(limiter.permitir("ip:1", 1, Duration.ofMinutes(15)));

        limiter.limpiar();

        assertTrue(limiter.permitir("ip:1", 1, Duration.ofMinutes(15)));
    }
}
