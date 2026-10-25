package com.fixtech.dto;

import java.math.BigDecimal;

public record ContactoTecnicoResponse(
        Long id,
        String nombres,
        String apellidos,
        String celular,
        String correo,
        String fotoUrl,
        BigDecimal calificacionPromedio,
        int totalResenas,
        String direccionTaller,
        String referenciaTaller,
        String horarioTaller) {
}
