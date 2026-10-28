package com.fixtech.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RecuperacionRateLimiter {

    private static final int MAX_CLAVES = 10_000;

    private final Map<String, Deque<Instant>> eventos = new ConcurrentHashMap<>();

    public boolean permitir(String clave, int maximo, Duration ventana) {
        String normalizada = clave == null || clave.isBlank() ? "desconocida" : clave;
        Instant ahora = Instant.now();
        Instant desde = ahora.minus(ventana);
        Deque<Instant> marcas = eventos.computeIfAbsent(normalizada, k -> new ArrayDeque<>());

        synchronized (marcas) {
            while (!marcas.isEmpty() && marcas.peekFirst().isBefore(desde)) {
                marcas.pollFirst();
            }
            if (marcas.size() >= maximo) {
                return false;
            }
            marcas.addLast(ahora);
        }

        if (eventos.size() > MAX_CLAVES) {
            eventos.entrySet().removeIf(entrada -> entrada.getValue().isEmpty());
        }
        return true;
    }

    public void limpiar() {
        eventos.clear();
    }
}
