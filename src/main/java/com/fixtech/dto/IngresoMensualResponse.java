package com.fixtech.dto;

import java.math.BigDecimal;

public record IngresoMensualResponse(
        String mes,
        long servicios,
        BigDecimal volumenTotal,
        BigDecimal ingresoFixtech) {
}
