package com.fixtech.dto;

import java.math.BigDecimal;

public record TecnicoResumenResponse(
        Long id,
        String nombres,
        String apellidos,
        String fotoUrl,
        String descripcion,
        int aniosExperiencia,
        BigDecimal calificacionPromedio,
        int totalResenas,
        int serviciosCompletados,
        boolean destacado) {
}
