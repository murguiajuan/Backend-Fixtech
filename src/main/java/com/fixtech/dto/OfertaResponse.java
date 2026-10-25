package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoOferta;
import com.fixtech.entity.enums.Modalidad;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OfertaResponse(
        Long id,
        Long solicitudId,
        EstadoOferta estado,
        Modalidad modalidad,
        BigDecimal precioServicio,
        BigDecimal costoMovilidad,
        BigDecimal costoDiagnostico,
        BigDecimal precioTotal,
        int tiempoEstimadoHoras,
        int garantiaDias,
        String mensaje,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn,
        TecnicoResumenResponse tecnico,
        List<ContraofertaResponse> contraofertas,
        Long servicioId) {
}
