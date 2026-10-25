package com.fixtech.service;

import com.fixtech.dto.IngresoMensualResponse;
import com.fixtech.dto.ReporteResponse;
import com.fixtech.dto.TecnicoActividadResponse;
import com.fixtech.entity.enums.EstadoComision;
import com.fixtech.entity.enums.EstadoOferta;
import com.fixtech.entity.enums.EstadoReclamo;
import com.fixtech.entity.enums.EstadoResena;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.EstadoSolicitud;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ComisionRepository;
import com.fixtech.repository.ReclamoRepository;
import com.fixtech.repository.ResenaRepository;
import com.fixtech.repository.ServicioRepository;
import com.fixtech.repository.SolicitudRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdministradorReporteService {

    private static final int MAX_DIAS_PERIODO = 366;
    private static final int MAX_TECNICOS_ACTIVOS = 10;
    private static final List<EstadoSolicitud> SOLICITUDES_ABIERTAS =
            List.of(EstadoSolicitud.PUBLICADA, EstadoSolicitud.CON_OFERTAS);
    private static final List<EstadoOferta> OFERTAS_VIGENTES =
            List.of(EstadoOferta.ENVIADA, EstadoOferta.CONTRAOFERTADA);

    private final AdministradorRepository administradorRepository;
    private final SolicitudRepository solicitudRepository;
    private final ServicioRepository servicioRepository;
    private final ComisionRepository comisionRepository;
    private final ResenaRepository resenaRepository;
    private final ReclamoRepository reclamoRepository;

    public ReporteResponse obtener(
            Long adminUsuarioId, LocalDate desde, LocalDate hasta) {
        validarAdministrador(adminUsuarioId);
        validarPeriodo(desde, hasta);

        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();

        return new ReporteResponse(
                desde,
                hasta,
                solicitudRepository
                        .countByCreadoEnGreaterThanEqualAndCreadoEnLessThan(inicio, fin),
                solicitudRepository.contarSinOfertasEntre(
                        inicio, fin, SOLICITUDES_ABIERTAS, OFERTAS_VIGENTES),
                servicioRepository
                        .countByCreadoEnGreaterThanEqualAndCreadoEnLessThan(inicio, fin),
                contarServiciosPorEstado(
                        servicioRepository.contarPorEstadoEntre(inicio, fin)),
                monto(comisionRepository.sumarVolumenEntre(
                        inicio, fin, EstadoComision.ANULADA)),
                monto(comisionRepository.sumarComisionEntre(
                        inicio, fin, EstadoComision.ANULADA)),
                ingresosPorMes(inicio, fin),
                tecnicosMasActivos(inicio, fin),
                promedio(resenaRepository.promedioEntre(
                        EstadoResena.VISIBLE, inicio, fin)),
                resenaRepository.contarEntre(EstadoResena.VISIBLE, inicio, fin),
                reclamoRepository
                        .countByCreadoEnGreaterThanEqualAndCreadoEnLessThan(inicio, fin),
                contarReclamosPorEstado(
                        reclamoRepository.contarPorEstadoEntre(inicio, fin)));
    }

    private void validarPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new BadRequestException("Debe indicar la fecha de inicio y de fin");
        }
        if (hasta.isBefore(desde)) {
            throw new BadRequestException(
                    "La fecha de fin no puede ser anterior a la fecha de inicio");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= MAX_DIAS_PERIODO) {
            throw new BadRequestException(
                    "El periodo del reporte no puede superar los %d días"
                            .formatted(MAX_DIAS_PERIODO));
        }
    }

    private List<IngresoMensualResponse> ingresosPorMes(
            LocalDateTime desde, LocalDateTime hasta) {
        return comisionRepository.ingresosPorMes(desde, hasta).stream()
                .map(fila -> new IngresoMensualResponse(
                        String.valueOf(fila[0]),
                        ((Number) fila[1]).longValue(),
                        monto(fila[2]),
                        monto(fila[3])))
                .toList();
    }

    private List<TecnicoActividadResponse> tecnicosMasActivos(
            LocalDateTime desde, LocalDateTime hasta) {
        return servicioRepository
                .tecnicosMasActivos(desde, hasta, EstadoServicio.CANCELADO)
                .stream()
                .limit(MAX_TECNICOS_ACTIVOS)
                .map(this::aTecnicoActividad)
                .toList();
    }

    private TecnicoActividadResponse aTecnicoActividad(Object[] fila) {
        return new TecnicoActividadResponse(
                ((Number) fila[0]).longValue(),
                fila[1] + " " + fila[2],
                ((Number) fila[4]).longValue(),
                promedio(fila[3] == null ? null : ((Number) fila[3]).doubleValue()));
    }

    private Map<EstadoServicio, Long> contarServiciosPorEstado(List<Object[]> filas) {
        Map<EstadoServicio, Long> conteo = new EnumMap<>(EstadoServicio.class);
        for (EstadoServicio estado : EstadoServicio.values()) {
            conteo.put(estado, 0L);
        }
        for (Object[] fila : filas) {
            conteo.put((EstadoServicio) fila[0], ((Number) fila[1]).longValue());
        }
        return conteo;
    }

    private Map<EstadoReclamo, Long> contarReclamosPorEstado(List<Object[]> filas) {
        Map<EstadoReclamo, Long> conteo = new EnumMap<>(EstadoReclamo.class);
        for (EstadoReclamo estado : EstadoReclamo.values()) {
            conteo.put(estado, 0L);
        }
        for (Object[] fila : filas) {
            conteo.put((EstadoReclamo) fila[0], ((Number) fila[1]).longValue());
        }
        return conteo;
    }

    private void validarAdministrador(Long adminUsuarioId) {
        administradorRepository.findByUsuarioId(adminUsuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de administrador"));
    }

    private BigDecimal monto(Object valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal numero = valor instanceof BigDecimal decimal
                ? decimal
                : new BigDecimal(valor.toString());
        return numero.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal promedio(Double valor) {
        return valor == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
    }
}
