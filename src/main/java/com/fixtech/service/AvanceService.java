package com.fixtech.service;

import com.fixtech.dto.AvanceRequest;
import com.fixtech.dto.AvanceResponse;
import com.fixtech.entity.AvanceServicio;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.TipoAvance;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.mapper.ServicioMapper;
import com.fixtech.repository.AvanceServicioRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AvanceService {

    private static final Set<EstadoServicio> ESTADOS_CON_AVANCES = EnumSet.of(
            EstadoServicio.EQUIPO_RECIBIDO,
            EstadoServicio.EN_PROCESO,
            EstadoServicio.EN_DIAGNOSTICO,
            EstadoServicio.EN_REPARACION,
            EstadoServicio.LISTO_PARA_RECOGER);

    private final AvanceServicioRepository avanceRepository;
    private final ServicioAccesoService servicioAccesoService;
    private final ServicioEventoService servicioEventoService;
    private final ArchivoService archivoService;
    private final ServicioMapper servicioMapper;

    @Transactional
    public AvanceResponse registrar(
            Long usuarioId,
            Long servicioId,
            AvanceRequest request,
            MultipartFile foto) {
        Servicio servicio = servicioAccesoService.bloquear(servicioId);
        servicioAccesoService.validarTecnico(servicio, usuarioId);

        if (!ESTADOS_CON_AVANCES.contains(servicio.getEstado())) {
            throw new BusinessException(
                    "No se pueden registrar avances en el estado actual del servicio");
        }
        if (request.tipo() == null) {
            throw new BadRequestException("Debe indicar el tipo de avance");
        }
        if (request.descripcion() == null || request.descripcion().isBlank()) {
            throw new BadRequestException("La descripción del avance es obligatoria");
        }
        if (request.descripcion().trim().length() > 1000) {
            throw new BadRequestException(
                    "La descripción no puede superar los 1000 caracteres");
        }
        if (request.tipo() == TipoAvance.DIAGNOSTICO
                && servicio.getEstado() != EstadoServicio.EN_DIAGNOSTICO) {
            throw new BusinessException(
                    "El diagnóstico se registra cuando el servicio está EN_DIAGNOSTICO");
        }

        String ruta = foto != null && !foto.isEmpty()
                ? archivoService.guardarImagenServicio(servicioId, "avances", foto)
                : null;
        try {
            AvanceServicio avance = AvanceServicio.builder()
                    .servicio(servicio)
                    .tipo(request.tipo())
                    .descripcion(request.descripcion().trim())
                    .rutaFoto(ruta)
                    .build();
            avanceRepository.save(avance);

            servicioEventoService.notificarContraparte(
                    servicio,
                    usuarioId,
                    TipoNotificacion.AVANCE_REGISTRADO,
                    request.tipo() == TipoAvance.DIAGNOSTICO
                            ? "Diagnóstico registrado"
                            : "Avance registrado",
                    request.tipo() == TipoAvance.DIAGNOSTICO
                            ? "El técnico registró el diagnóstico del equipo."
                            : "El técnico subió un nuevo avance del servicio.");

            return servicioMapper.toAvance(avance);
        } catch (RuntimeException ex) {
            if (ruta != null) {
                archivoService.eliminar(ruta);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<AvanceResponse> listar(Long usuarioId, Long servicioId) {
        servicioAccesoService.obtenerParaParticipante(usuarioId, servicioId);
        return avanceRepository.findByServicioIdOrderByCreadoEnAscIdAsc(servicioId).stream()
                .map(servicioMapper::toAvance)
                .toList();
    }

    @Transactional(readOnly = true)
    public ArchivoService.RecursoImagen obtenerFoto(
            Long usuarioId, Long servicioId, Long avanceId) {
        servicioAccesoService.obtenerParaParticipante(usuarioId, servicioId);
        AvanceServicio avance = avanceRepository.findByIdAndServicioId(avanceId, servicioId)
                .orElseThrow(() -> new NotFoundException("Avance no encontrado"));
        if (avance.getRutaFoto() == null) {
            throw new NotFoundException("El avance no tiene foto");
        }
        return archivoService.cargar(avance.getRutaFoto());
    }
}
