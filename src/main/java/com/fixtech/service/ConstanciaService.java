package com.fixtech.service;

import com.fixtech.dto.ConstanciaRequest;
import com.fixtech.dto.ConstanciaResponse;
import com.fixtech.entity.Constancia;
import com.fixtech.entity.ConstanciaFoto;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.TipoConstancia;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.mapper.ServicioMapper;
import com.fixtech.repository.ConstanciaFotoRepository;
import com.fixtech.repository.ConstanciaRepository;
import com.fixtech.repository.ServicioRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ConstanciaService {

    private static final int MIN_FOTOS = 1;
    private static final int MAX_FOTOS = 5;

    private final ConstanciaRepository constanciaRepository;
    private final ConstanciaFotoRepository constanciaFotoRepository;
    private final ServicioRepository servicioRepository;
    private final ServicioAccesoService servicioAccesoService;
    private final ServicioEventoService servicioEventoService;
    private final ArchivoService archivoService;
    private final ServicioMapper servicioMapper;

    @Transactional
    public ConstanciaResponse registrar(
            Long usuarioId,
            Long servicioId,
            ConstanciaRequest request,
            List<MultipartFile> fotos) {
        Servicio servicio = servicioAccesoService.bloquear(servicioId);
        boolean esCliente = servicioAccesoService.esCliente(servicio, usuarioId);
        boolean esTecnico = servicioAccesoService.esTecnico(servicio, usuarioId);
        if (!esCliente && !esTecnico) {
            throw new com.fixtech.exception.ForbiddenException(
                    "No tiene acceso a este servicio");
        }
        if (servicio.getModalidad() != Modalidad.TALLER) {
            throw new BusinessException(
                    "Las constancias solo aplican a la modalidad en taller");
        }
        validarFotos(fotos);
        validarTextos(request);
        if (constanciaRepository.existsByServicioIdAndTipo(
                servicioId, request.tipo())) {
            throw new BusinessException(
                    "Ya existe una constancia de %s para este servicio"
                            .formatted(request.tipo()));
        }
        validarEstado(servicio, request.tipo());

        List<String> rutas = new ArrayList<>();
        try {
            for (MultipartFile foto : fotos) {
                rutas.add(archivoService.guardarImagenServicio(
                        servicioId, "constancias", foto));
            }

            LocalDateTime ahora = LocalDateTime.now();
            Constancia constancia = Constancia.builder()
                    .servicio(servicio)
                    .tipo(request.tipo())
                    .observaciones(normalizarTexto(request.observaciones()))
                    .accesorios(normalizarTexto(request.accesorios()))
                    .confirmadaPorCliente(esCliente)
                    .fechaConfirmacion(esCliente ? ahora : null)
                    .build();
            for (String ruta : rutas) {
                constancia.getFotos().add(ConstanciaFoto.builder()
                        .constancia(constancia)
                        .rutaArchivo(ruta)
                        .build());
            }
            constanciaRepository.save(constancia);

            if (request.tipo() == TipoConstancia.RECEPCION
                    && servicio.getEstado() == EstadoServicio.PROGRAMADO) {
                EstadoServicio anterior = servicio.getEstado();
                servicio.setEstado(EstadoServicio.EQUIPO_RECIBIDO);
                servicioRepository.save(servicio);
                servicioEventoService.registrarCambio(
                        servicio,
                        anterior,
                        EstadoServicio.EQUIPO_RECIBIDO,
                        usuarioId,
                        "Constancia de recepción registrada");
            }

            servicioEventoService.notificarContraparte(
                    servicio,
                    usuarioId,
                    TipoNotificacion.CONSTANCIA_REGISTRADA,
                    "Constancia de %s registrada"
                            .formatted(request.tipo() == TipoConstancia.RECEPCION
                                    ? "recepción"
                                    : "entrega"),
                    "Se registró la constancia con %d foto(s)."
                            .formatted(rutas.size()));

            return servicioMapper.toConstancia(constancia);
        } catch (RuntimeException ex) {
            rutas.forEach(archivoService::eliminar);
            throw ex;
        }
    }

    @Transactional
    public ConstanciaResponse confirmar(
            Long usuarioId, Long servicioId, Long constanciaId) {
        Servicio servicio = servicioAccesoService.bloquear(servicioId);
        servicioAccesoService.validarCliente(servicio, usuarioId);

        Constancia constancia = constanciaRepository.buscarDetalle(
                        constanciaId, servicioId)
                .orElseThrow(() -> new com.fixtech.exception.NotFoundException(
                        "Constancia no encontrada"));
        if (!constancia.isConfirmadaPorCliente()) {
            constancia.setConfirmadaPorCliente(true);
            constancia.setFechaConfirmacion(LocalDateTime.now());
            constanciaRepository.save(constancia);
            servicioEventoService.notificarContraparte(
                    servicio,
                    usuarioId,
                    TipoNotificacion.CONSTANCIA_CONFIRMADA,
                    "Constancia confirmada",
                    "El cliente confirmó la constancia de %s."
                            .formatted(constancia.getTipo() == TipoConstancia.RECEPCION
                                    ? "recepción"
                                    : "entrega"));
        }
        return servicioMapper.toConstancia(constancia);
    }

    @Transactional(readOnly = true)
    public ArchivoService.RecursoImagen obtenerFoto(
            Long usuarioId, Long servicioId, Long constanciaId, Long fotoId) {
        servicioAccesoService.obtenerParaParticipante(usuarioId, servicioId);
        ConstanciaFoto foto = constanciaFotoRepository
                .findByIdAndConstanciaIdAndConstanciaServicioId(
                        fotoId, constanciaId, servicioId)
                .orElseThrow(() -> new com.fixtech.exception.NotFoundException(
                        "Foto de constancia no encontrada"));
        return archivoService.cargar(foto.getRutaArchivo());
    }

    private void validarFotos(List<MultipartFile> fotos) {
        if (fotos == null || fotos.size() < MIN_FOTOS || fotos.size() > MAX_FOTOS) {
            throw new BadRequestException(
                    "Debe adjuntar entre %d y %d fotos".formatted(MIN_FOTOS, MAX_FOTOS));
        }
    }

    private void validarTextos(ConstanciaRequest request) {
        if (request.tipo() == null) {
            throw new BadRequestException("Debe indicar el tipo de constancia");
        }
        if (request.observaciones() != null && request.observaciones().length() > 1000) {
            throw new BadRequestException(
                    "Las observaciones no pueden superar los 1000 caracteres");
        }
        if (request.accesorios() != null && request.accesorios().length() > 300) {
            throw new BadRequestException(
                    "Los accesorios no pueden superar los 300 caracteres");
        }
    }

    private void validarEstado(Servicio servicio, TipoConstancia tipo) {
        if (tipo == TipoConstancia.RECEPCION) {
            if (servicio.getEstado() != EstadoServicio.PROGRAMADO
                    && servicio.getEstado() != EstadoServicio.EQUIPO_RECIBIDO) {
                throw new BusinessException(
                        "La constancia de recepción se registra al inicio del servicio");
            }
            return;
        }
        if (servicio.getEstado() != EstadoServicio.LISTO_PARA_RECOGER) {
            throw new BusinessException(
                    "La constancia de entrega se registra cuando el equipo está listo para recoger");
        }
    }

    private String normalizarTexto(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
