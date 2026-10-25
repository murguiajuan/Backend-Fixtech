package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoReclamo;
import com.fixtech.entity.enums.EstadoServicio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ReporteResponse(
        LocalDate desde,
        LocalDate hasta,
        long totalSolicitudes,
        long solicitudesSinOfertas,
        long totalServicios,
        Map<EstadoServicio, Long> serviciosPorEstado,
        BigDecimal volumenServicios,
        BigDecimal ingresoComisiones,
        List<IngresoMensualResponse> ingresosPorMes,
        List<TecnicoActividadResponse> tecnicosMasActivos,
        BigDecimal calificacionPromedio,
        long totalResenas,
        long totalReclamos,
        Map<EstadoReclamo, Long> reclamosPorEstado) {
}
