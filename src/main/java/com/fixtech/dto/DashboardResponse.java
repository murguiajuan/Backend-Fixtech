package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoServicio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        long totalSolicitudes,
        long solicitudesSinOfertas,
        long totalServicios,
        Map<EstadoServicio, Long> serviciosPorEstado,
        BigDecimal ingresoTotalComisiones,
        BigDecimal ingresoMesActual,
        long comisionesPendientes,
        BigDecimal deudaComisionesPendiente,
        long pagosPorValidar,
        long tecnicosActivos,
        BigDecimal calificacionPromedio,
        long totalResenas,
        long reclamosAbiertos,
        long resenasReportadas,
        List<TecnicoActividadResponse> tecnicosMasActivos) {
}
