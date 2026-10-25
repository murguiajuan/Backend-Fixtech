package com.fixtech.dto;

import java.time.LocalDateTime;

public record MensajeResponse(
        Long id,
        Long emisorId,
        String emisorNombre,
        String contenido,
        boolean leido,
        boolean mio,
        LocalDateTime creadoEn) {
}
