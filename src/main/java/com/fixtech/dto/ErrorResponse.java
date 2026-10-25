package com.fixtech.dto;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

public record ErrorResponse(
        Instant timestamp,
        int estado,
        String error,
        String mensaje,
        String ruta,
        List<String> detalles) {

    public static ErrorResponse de(HttpStatus estado, String mensaje, String ruta) {
        return de(estado, mensaje, ruta, List.of());
    }

    public static ErrorResponse de(HttpStatus estado, String mensaje, String ruta, List<String> detalles) {
        return new ErrorResponse(
                Instant.now(),
                estado.value(),
                estado.getReasonPhrase(),
                mensaje,
                ruta,
                List.copyOf(detalles));
    }
}
