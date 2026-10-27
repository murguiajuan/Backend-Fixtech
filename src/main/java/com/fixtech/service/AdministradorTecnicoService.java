package com.fixtech.service;

import com.fixtech.dto.TecnicoAdminResponse;
import com.fixtech.dto.TecnicoVerificacionResponse;
import com.fixtech.dto.VerificacionTecnicoRequest;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.TecnicoRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdministradorTecnicoService {

    private static final int MAX_BUSQUEDA = 120;

    private final TecnicoRepository tecnicoRepository;
    private final AdministradorRepository administradorRepository;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<TecnicoAdminResponse> listar(
            EstadoVerificacion estadoVerificacion, String busqueda) {
        String filtro = normalizar(busqueda);
        if (filtro != null && filtro.length() > MAX_BUSQUEDA) {
            throw new BadRequestException(
                    "La búsqueda no puede superar los %d caracteres"
                            .formatted(MAX_BUSQUEDA));
        }
        String patron = filtro == null
                ? ""
                : "%" + filtro.toLowerCase(Locale.ROOT) + "%";
        String texto = filtro == null ? "" : filtro.toLowerCase(Locale.ROOT);
        List<Tecnico> tecnicos = estadoVerificacion == null
                ? tecnicoRepository.buscarParaAdministracion(texto, patron)
                : tecnicoRepository.buscarParaAdministracionPorEstado(
                        estadoVerificacion, texto, patron);
        return tecnicos.stream().map(this::aResponse).toList();
    }

    @Transactional
    public TecnicoVerificacionResponse verificar(
            Long adminUsuarioId, Long tecnicoId, VerificacionTecnicoRequest request) {
        administradorRepository.findByUsuarioId(adminUsuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de administrador"));

        Tecnico tecnico = tecnicoRepository.findById(tecnicoId)
                .orElseThrow(() -> new NotFoundException("Técnico no encontrado"));

        EstadoVerificacion estado = request.estado();
        if (estado == EstadoVerificacion.PENDIENTE) {
            throw new BadRequestException(
                    "El estado de verificación debe ser APROBADO, OBSERVADO o RECHAZADO");
        }

        String motivo = StringUtils.hasText(request.motivo())
                ? request.motivo().trim()
                : null;
        if (estado != EstadoVerificacion.APROBADO && motivo == null) {
            throw new BadRequestException(
                    "El motivo es obligatorio para observar o rechazar al técnico");
        }
        if (estado == EstadoVerificacion.APROBADO) {
            motivo = null;
        }

        EstadoVerificacion estadoAnterior = tecnico.getEstadoVerificacion();
        tecnico.setEstadoVerificacion(estado);
        tecnico.setMotivoVerificacion(motivo);
        tecnico.setVerificadoPorId(adminUsuarioId);
        tecnico.setFechaVerificacion(LocalDateTime.now());
        tecnicoRepository.save(tecnico);

        notificarTecnico(tecnico, estado, motivo);
        auditoriaService.registrar(
                adminUsuarioId,
                "VERIFICAR_TECNICO",
                "tecnico",
                tecnico.getId(),
                "Estado: %s -> %s%s".formatted(
                        estadoAnterior,
                        estado,
                        motivo == null ? "" : ". Motivo: " + motivo));

        return new TecnicoVerificacionResponse(
                tecnico.getId(),
                tecnico.getEstadoVerificacion(),
                tecnico.getMotivoVerificacion(),
                tecnico.getFechaVerificacion());
    }

    private void notificarTecnico(
            Tecnico tecnico, EstadoVerificacion estado, String motivo) {
        String titulo;
        String mensaje;
        switch (estado) {
            case APROBADO -> {
                titulo = "Técnico aprobado";
                mensaje = "Su cuenta fue aprobada. Ya puede enviar ofertas a las solicitudes compatibles.";
            }
            case OBSERVADO -> {
                titulo = "Verificación observada";
                mensaje = "Su verificación requiere correcciones: " + motivo;
            }
            case RECHAZADO -> {
                titulo = "Verificación rechazada";
                mensaje = "Su verificación fue rechazada: " + motivo;
            }
            default -> throw new BadRequestException(
                    "El estado de verificación no permite notificar al técnico");
        }
        notificacionService.crear(
                tecnico.getUsuario().getId(),
                TipoNotificacion.VERIFICACION_TECNICO,
                titulo,
                mensaje,
                "TECNICO",
                tecnico.getId());
    }

    private TecnicoAdminResponse aResponse(Tecnico tecnico) {
        return new TecnicoAdminResponse(
                tecnico.getId(),
                tecnico.getUsuario().getId(),
                tecnico.getUsuario().getCorreo(),
                tecnico.getNombres(),
                tecnico.getApellidos(),
                tecnico.getDni(),
                tecnico.getCelular(),
                tecnico.getFotoUrl(),
                tecnico.getEstadoVerificacion(),
                tecnico.getMotivoVerificacion(),
                tecnico.isBloqueadoPorDeuda(),
                tecnico.getSaldoComisionPendiente(),
                modalidades(tecnico),
                tecnico.getCalificacionPromedio(),
                tecnico.getTotalResenas(),
                tecnico.getServiciosCompletados(),
                tecnico.isDestacado(),
                tecnico.getFechaVerificacion(),
                tecnico.getCreadoEn());
    }

    private List<Modalidad> modalidades(Tecnico tecnico) {
        List<Modalidad> modalidades = new ArrayList<>(2);
        if (tecnico.isOfreceDomicilio()) {
            modalidades.add(Modalidad.DOMICILIO);
        }
        if (tecnico.isOfreceTaller()) {
            modalidades.add(Modalidad.TALLER);
        }
        return List.copyOf(modalidades);
    }

    private String normalizar(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
