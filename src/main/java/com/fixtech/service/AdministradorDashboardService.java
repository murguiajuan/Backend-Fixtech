package com.fixtech.service;

import com.fixtech.dto.DashboardResponse;
import com.fixtech.dto.TecnicoActividadResponse;
import com.fixtech.entity.enums.EstadoComision;
import com.fixtech.entity.enums.EstadoPagoComision;
import com.fixtech.entity.enums.EstadoReclamo;
import com.fixtech.entity.enums.EstadoResena;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.EstadoOferta;
import com.fixtech.entity.enums.EstadoSolicitud;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ComisionRepository;
import com.fixtech.repository.PagoComisionRepository;
import com.fixtech.repository.ReclamoRepository;
import com.fixtech.repository.ResenaRepository;
import com.fixtech.repository.ServicioRepository;
import com.fixtech.repository.SolicitudRepository;
import com.fixtech.repository.TecnicoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdministradorDashboardService {

    private static final int MAX_TECNICOS_ACTIVOS = 5;
    private static final List<EstadoSolicitud> SOLICITUDES_ABIERTAS =
            List.of(EstadoSolicitud.PUBLICADA, EstadoSolicitud.CON_OFERTAS);
    private static final List<EstadoOferta> OFERTAS_VIGENTES =
            List.of(EstadoOferta.ENVIADA, EstadoOferta.CONTRAOFERTADA);
    private static final List<EstadoReclamo> RECLAMOS_ABIERTOS =
            List.of(EstadoReclamo.ABIERTO, EstadoReclamo.EN_REVISION);

    private final AdministradorRepository administradorRepository;
    private final SolicitudRepository solicitudRepository;
    private final ServicioRepository servicioRepository;
    private final ComisionRepository comisionRepository;
    private final PagoComisionRepository pagoComisionRepository;
    private final TecnicoRepository tecnicoRepository;
    private final ResenaRepository resenaRepository;
    private final ReclamoRepository reclamoRepository;

    public DashboardResponse obtener(Long adminUsuarioId) {
        validarAdministrador(adminUsuarioId);

        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDateTime desdeMes = inicioMes.atStartOfDay();
        LocalDateTime hastaMes = inicioMes.plusMonths(1).atStartOfDay();

        return new DashboardResponse(
                solicitudRepository.count(),
                solicitudRepository.contarSinOfertas(SOLICITUDES_ABIERTAS, OFERTAS_VIGENTES),
                servicioRepository.count(),
                contarServiciosPorEstado(servicioRepository.contarPorEstado()),
                monto(comisionRepository.sumarComisionTotal(EstadoComision.ANULADA)),
                monto(comisionRepository.sumarComisionEntre(
                        desdeMes, hastaMes, EstadoComision.ANULADA)),
                comisionRepository.countByEstado(EstadoComision.PENDIENTE),
                monto(comisionRepository.sumarMontoPorEstado(EstadoComision.PENDIENTE)),
                pagoComisionRepository.countByEstado(EstadoPagoComision.PENDIENTE),
                tecnicoRepository.contarActivos(
                        EstadoVerificacion.APROBADO, EstadoUsuario.ACTIVO),
                promedio(resenaRepository.promedioGlobal(EstadoResena.VISIBLE)),
                resenaRepository.countByEstado(EstadoResena.VISIBLE),
                reclamoRepository.countByEstadoIn(RECLAMOS_ABIERTOS),
                resenaRepository.countByEstado(EstadoResena.REPORTADA),
                tecnicosMasActivos(desdeMes, hastaMes));
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

    private void validarAdministrador(Long adminUsuarioId) {
        administradorRepository.findByUsuarioId(adminUsuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de administrador"));
    }

    private BigDecimal monto(BigDecimal valor) {
        return valor == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal promedio(Double valor) {
        return valor == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
    }
}
