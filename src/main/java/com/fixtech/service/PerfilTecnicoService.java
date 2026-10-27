package com.fixtech.service;

import com.fixtech.dto.PerfilTecnicoRequest;
import com.fixtech.dto.PerfilUsuarioResponse;
import com.fixtech.entity.Distrito;
import com.fixtech.entity.Especialidad;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.DistritoRepository;
import com.fixtech.repository.EspecialidadRepository;
import com.fixtech.repository.TecnicoRepository;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PerfilTecnicoService {

    private final TecnicoRepository tecnicoRepository;
    private final EspecialidadRepository especialidadRepository;
    private final DistritoRepository distritoRepository;
    private final PerfilUsuarioService perfilUsuarioService;
    private final ArchivoService archivoService;

    @Transactional
    public PerfilUsuarioResponse actualizar(Long usuarioId, PerfilTecnicoRequest request) {
        Tecnico tecnico = obtenerTecnico(usuarioId);
        validarModalidades(request);
        validarDniDisponible(request.dni(), tecnico.getId());

        tecnico.setNombres(request.nombres().trim());
        tecnico.setApellidos(request.apellidos().trim());
        tecnico.setDni(request.dni());
        tecnico.setCelular(request.celular());
        tecnico.setDescripcion(textoONulo(request.descripcion()));
        tecnico.setAniosExperiencia((byte) request.aniosExperiencia());
        tecnico.setOfreceDomicilio(request.ofreceDomicilio());
        tecnico.setOfreceTaller(request.ofreceTaller());
        tecnico.setDireccionTaller(textoONulo(request.direccionTaller()));
        tecnico.setReferenciaTaller(textoONulo(request.referenciaTaller()));
        tecnico.setHorarioTaller(textoONulo(request.horarioTaller()));
        tecnico.setGarantiaDiasDefecto(request.garantiaDiasDefecto());
        tecnico.setEspecialidades(obtenerEspecialidades(request.especialidadIds()));
        tecnico.setDistritos(obtenerDistritos(request.distritoIds()));
        reiniciarVerificacion(tecnico);

        tecnicoRepository.save(tecnico);
        return perfilUsuarioService.obtener(usuarioId);
    }

    @Transactional
    public PerfilUsuarioResponse actualizarFoto(Long usuarioId, MultipartFile foto) {
        Tecnico tecnico = obtenerTecnico(usuarioId);
        String anterior = tecnico.getFotoUrl();
        String ruta = archivoService.guardarFotoPerfilTecnico(tecnico.getId(), foto);
        tecnico.setFotoUrl(ruta);
        try {
            tecnicoRepository.save(tecnico);
        } catch (RuntimeException ex) {
            archivoService.eliminar(ruta);
            throw ex;
        }
        if (StringUtils.hasText(anterior) && !anterior.equals(ruta)) {
            archivoService.eliminar(anterior);
        }
        return perfilUsuarioService.obtener(usuarioId);
    }

    @Transactional(readOnly = true)
    public ArchivoService.RecursoImagen obtenerFoto(Long tecnicoId) {
        Tecnico tecnico = tecnicoRepository.findById(tecnicoId)
                .orElseThrow(() -> new NotFoundException("Técnico no encontrado"));
        if (!StringUtils.hasText(tecnico.getFotoUrl())) {
            throw new NotFoundException("El técnico no tiene foto de perfil");
        }
        return archivoService.cargar(tecnico.getFotoUrl());
    }

    private Tecnico obtenerTecnico(Long usuarioId) {
        return tecnicoRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de técnico"));
    }

    private void validarModalidades(PerfilTecnicoRequest request) {
        if (!request.ofreceDomicilio() && !request.ofreceTaller()) {
            throw new BusinessException("El técnico debe ofrecer al menos una modalidad");
        }
        if (request.ofreceTaller() && !StringUtils.hasText(request.direccionTaller())) {
            throw new BusinessException(
                    "La dirección del taller es obligatoria cuando se ofrece atención en taller");
        }
    }

    private void validarDniDisponible(String dni, Long tecnicoId) {
        if (tecnicoRepository.existsByDniAndIdNot(dni, tecnicoId)) {
            throw new BusinessException("El DNI ya está registrado");
        }
    }

    private Set<Especialidad> obtenerEspecialidades(Set<Long> ids) {
        Set<Especialidad> encontradas = new HashSet<>(especialidadRepository.findAllById(ids));
        if (encontradas.size() != ids.size()
                || encontradas.stream().anyMatch(especialidad -> !especialidad.isActivo())) {
            throw new BusinessException("Una o más especialidades no existen o están inactivas");
        }
        return encontradas;
    }

    private Set<Distrito> obtenerDistritos(Set<Long> ids) {
        Set<Distrito> encontrados = new HashSet<>(distritoRepository.findAllById(ids));
        if (encontrados.size() != ids.size()
                || encontrados.stream().anyMatch(distrito -> !distrito.isActivo())) {
            throw new BusinessException("Uno o más distritos no existen o están inactivos");
        }
        return encontrados;
    }

    private void reiniciarVerificacion(Tecnico tecnico) {
        tecnico.setEstadoVerificacion(EstadoVerificacion.PENDIENTE);
        tecnico.setMotivoVerificacion(null);
        tecnico.setVerificadoPorId(null);
        tecnico.setFechaVerificacion(null);
    }

    private String textoONulo(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
