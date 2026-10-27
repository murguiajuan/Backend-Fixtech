package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.Modalidad;
import java.math.BigDecimal;
import java.util.List;

public record PerfilTecnicoResponse(
        Long id,
        EstadoVerificacion estadoVerificacion,
        String motivoVerificacion,
        boolean bloqueadoPorDeuda,
        BigDecimal saldoComisionPendiente,
        List<Modalidad> modalidades,
        String descripcion,
        int aniosExperiencia,
        BigDecimal calificacionPromedio,
        int totalResenas,
        int serviciosCompletados,
        boolean destacado) {
}
