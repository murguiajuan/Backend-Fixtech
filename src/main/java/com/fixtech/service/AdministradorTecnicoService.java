package com.fixtech.service;

import com.fixtech.dto.TecnicoVerificacionResponse;
import com.fixtech.dto.VerificacionTecnicoRequest;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.TecnicoRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdministradorTecnicoService {

    private final TecnicoRepository tecnicoRepository;
    private final AdministradorRepository administradorRepository;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;

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
}
