package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.Modalidad;
import java.math.BigDecimal;
import java.util.List;

public record PerfilTecnicoResponse(
        Long id,
        String dni,
        String celular,
        EstadoVerificacion estadoVerificacion,
        String motivoVerificacion,
        boolean bloqueadoPorDeuda,
        BigDecimal saldoComisionPendiente,
        List<Modalidad> modalidades,
        boolean ofreceDomicilio,
        boolean ofreceTaller,
        List<CatalogoSimpleResponse> especialidades,
        List<CatalogoSimpleResponse> distritos,
        String descripcion,
        int aniosExperiencia,
        String direccionTaller,
        String referenciaTaller,
        String horarioTaller,
        int garantiaDiasDefecto,
        BigDecimal calificacionPromedio,
        int totalResenas,
        int serviciosCompletados,
        boolean destacado) {
}
