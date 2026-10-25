package com.fixtech.dto;

import java.math.BigDecimal;

public record DistritoResponse(
        Long id,
        String nombre,
        String provincia,
        BigDecimal topeMovilidad,
        boolean activo) {
}
