package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServicioResponse(
        Long id,
        Long solicitudId,
        Long ofertaId,
        Long clienteId,
        Long tecnicoId,
        Modalidad modalidad,
        EstadoServicio estado,
        BigDecimal precioServicio,
        BigDecimal costoMovilidad,
        BigDecimal costoDiagnostico,
        BigDecimal montoTotal,
        BigDecimal montoFinal,
        int garantiaDias,
        LocalDateTime creadoEn) {
}
