package com.fixtech.dto;

import com.fixtech.entity.enums.CanceladoPor;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.MetodoPago;
import com.fixtech.entity.enums.Modalidad;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ServicioDetalleResponse(
        Long id,
        Long solicitudId,
        Long ofertaId,
        Modalidad modalidad,
        EstadoServicio estado,
        BigDecimal precioServicio,
        BigDecimal costoMovilidad,
        BigDecimal costoDiagnostico,
        BigDecimal montoTotal,
        BigDecimal montoFinal,
        MetodoPago metodoPago,
        String pinInicio,
        boolean pinValidado,
        int garantiaDias,
        LocalDateTime fechaProgramada,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFinalizacion,
        LocalDateTime fechaLimiteConformidad,
        LocalDateTime fechaConfirmacion,
        boolean confirmadoAutomaticamente,
        CanceladoPor canceladoPor,
        String motivoCancelacion,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn,
        ContactoClienteResponse cliente,
        ContactoTecnicoResponse tecnico,
        SolicitudServicioResponse solicitud,
        List<HistorialEstadoResponse> historial,
        List<ConstanciaResponse> constancias,
        List<AvanceResponse> avances,
        List<AjustePrecioResponse> ajustes,
        AjustePrecioResponse ajustePendiente) {
}
