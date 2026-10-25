package com.fixtech.dto;

import java.time.LocalDateTime;

public record ParametroResponse(
        String clave,
        String valor,
        String descripcion,
        LocalDateTime actualizadoEn,
        Long actualizadoPorId) {
}
