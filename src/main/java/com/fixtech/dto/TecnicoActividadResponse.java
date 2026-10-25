package com.fixtech.dto;

import java.math.BigDecimal;

public record TecnicoActividadResponse(
        Long tecnicoId,
        String nombre,
        long servicios,
        BigDecimal calificacionPromedio) {
}
