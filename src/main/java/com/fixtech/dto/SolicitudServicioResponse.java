package com.fixtech.dto;

import com.fixtech.entity.enums.Urgencia;
import java.time.LocalDate;

public record SolicitudServicioResponse(
        Long id,
        String descripcion,
        String tipoEquipo,
        String tipoServicio,
        String marca,
        String modelo,
        Urgencia urgencia,
        String distrito,
        String direccion,
        String referencia,
        LocalDate fechaPreferida,
        String horarioPreferido) {
}
