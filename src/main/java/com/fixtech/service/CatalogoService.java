package com.fixtech.service;

import com.fixtech.dto.CatalogoSimpleRequest;
import com.fixtech.dto.CatalogoSimpleResponse;
import com.fixtech.dto.DistritoRequest;
import com.fixtech.dto.DistritoResponse;
import com.fixtech.dto.EspecialidadRequest;
import com.fixtech.dto.EspecialidadResponse;
import com.fixtech.entity.Distrito;
import com.fixtech.entity.Especialidad;
import com.fixtech.entity.TipoEquipo;
import com.fixtech.entity.TipoServicio;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.DistritoRepository;
import com.fixtech.repository.EspecialidadRepository;
import com.fixtech.repository.TipoEquipoRepository;
import com.fixtech.repository.TipoServicioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogoService {

    private static final String PROVINCIA_POR_DEFECTO = "Piura";
    private static final int MAX_NOMBRE_EQUIPO = 60;
    private static final int MAX_NOMBRE_SERVICIO = 80;
    private static final int MAX_NOMBRE_ESPECIALIDAD = 80;
    private static final int MAX_DESCRIPCION = 200;
    private static final int MAX_NOMBRE_DISTRITO = 60;
    private static final int MAX_PROVINCIA = 60;
    private static final BigDecimal TOPE_MOVILIDAD_MAXIMO = new BigDecimal("1000000.00");

    private final TipoEquipoRepository tipoEquipoRepository;
    private final TipoServicioRepository tipoServicioRepository;
    private final EspecialidadRepository especialidadRepository;
    private final DistritoRepository distritoRepository;
    private final AdministradorRepository administradorRepository;
    private final AuditoriaService auditoriaService;

    public List<CatalogoSimpleResponse> listarTiposEquipo(boolean soloActivos) {
        List<TipoEquipo> tipos = soloActivos
                ? tipoEquipoRepository.findByActivoTrueOrderByNombreAsc()
                : tipoEquipoRepository.findAllByOrderByNombreAsc();
        return tipos.stream().map(this::aResponse).toList();
    }

    public List<CatalogoSimpleResponse> listarTiposServicio(boolean soloActivos) {
        List<TipoServicio> tipos = soloActivos
                ? tipoServicioRepository.findByActivoTrueOrderByNombreAsc()
                : tipoServicioRepository.findAllByOrderByNombreAsc();
        return tipos.stream().map(this::aResponse).toList();
    }

    public List<EspecialidadResponse> listarEspecialidades(boolean soloActivos) {
        List<Especialidad> especialidades = soloActivos
                ? especialidadRepository.findByActivoTrueOrderByNombreAsc()
                : especialidadRepository.findAllByOrderByNombreAsc();
        return especialidades.stream().map(this::aResponse).toList();
    }

    public List<DistritoResponse> listarDistritos(boolean soloActivos) {
        List<Distrito> distritos = soloActivos
                ? distritoRepository.findByActivoTrueOrderByNombreAsc()
                : distritoRepository.findAllByOrderByNombreAsc();
        return distritos.stream().map(this::aResponse).toList();
    }

    @Transactional
    public CatalogoSimpleResponse crearTipoEquipo(
            Long adminUsuarioId, CatalogoSimpleRequest request) {
        validarAdministrador(adminUsuarioId);
        String nombre = normalizarObligatorio(
                request == null ? null : request.nombre(),
                MAX_NOMBRE_EQUIPO,
                "El nombre es obligatorio");
        if (tipoEquipoRepository.existsByNombreIgnoreCase(nombre)) {
            throw new BusinessException("Ya existe un tipo de equipo con ese nombre");
        }
        TipoEquipo tipo = tipoEquipoRepository.save(TipoEquipo.builder()
                .nombre(nombre)
                .activo(true)
                .build());
        auditar(adminUsuarioId, "CREAR_CATALOGO", "tipo_equipo", tipo.getId(),
                "Nombre: " + nombre);
        return aResponse(tipo);
    }

    @Transactional
    public CatalogoSimpleResponse actualizarTipoEquipo(
            Long adminUsuarioId, Long id, CatalogoSimpleRequest request) {
        validarAdministrador(adminUsuarioId);
        TipoEquipo tipo = tipoEquipoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tipo de equipo no encontrado"));
        String nombre = normalizarObligatorio(
                request == null ? null : request.nombre(),
                MAX_NOMBRE_EQUIPO,
                "El nombre es obligatorio");
        if (tipoEquipoRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new BusinessException("Ya existe un tipo de equipo con ese nombre");
        }
        String anterior = tipo.getNombre();
        tipo.setNombre(nombre);
        tipoEquipoRepository.save(tipo);
        auditar(adminUsuarioId, "ACTUALIZAR_CATALOGO", "tipo_equipo", tipo.getId(),
                "Nombre: %s -> %s".formatted(anterior, nombre));
        return aResponse(tipo);
    }

    @Transactional
    public CatalogoSimpleResponse cambiarEstadoTipoEquipo(
            Long adminUsuarioId, Long id, Boolean activo) {
        validarAdministrador(adminUsuarioId);
        TipoEquipo tipo = tipoEquipoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tipo de equipo no encontrado"));
        boolean nuevo = validarActivo(activo);
        if (tipo.isActivo() != nuevo) {
            tipo.setActivo(nuevo);
            tipoEquipoRepository.save(tipo);
            auditarEstado(adminUsuarioId, "tipo_equipo", tipo.getId(), nuevo);
        }
        return aResponse(tipo);
    }

    @Transactional
    public CatalogoSimpleResponse crearTipoServicio(
            Long adminUsuarioId, CatalogoSimpleRequest request) {
        validarAdministrador(adminUsuarioId);
        String nombre = normalizarObligatorio(
                request == null ? null : request.nombre(),
                MAX_NOMBRE_SERVICIO,
                "El nombre es obligatorio");
        if (tipoServicioRepository.existsByNombreIgnoreCase(nombre)) {
            throw new BusinessException("Ya existe un tipo de servicio con ese nombre");
        }
        TipoServicio tipo = tipoServicioRepository.save(TipoServicio.builder()
                .nombre(nombre)
                .activo(true)
                .build());
        auditar(adminUsuarioId, "CREAR_CATALOGO", "tipo_servicio", tipo.getId(),
                "Nombre: " + nombre);
        return aResponse(tipo);
    }

    @Transactional
    public CatalogoSimpleResponse actualizarTipoServicio(
            Long adminUsuarioId, Long id, CatalogoSimpleRequest request) {
        validarAdministrador(adminUsuarioId);
        TipoServicio tipo = tipoServicioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tipo de servicio no encontrado"));
        String nombre = normalizarObligatorio(
                request == null ? null : request.nombre(),
                MAX_NOMBRE_SERVICIO,
                "El nombre es obligatorio");
        if (tipoServicioRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new BusinessException("Ya existe un tipo de servicio con ese nombre");
        }
        String anterior = tipo.getNombre();
        tipo.setNombre(nombre);
        tipoServicioRepository.save(tipo);
        auditar(adminUsuarioId, "ACTUALIZAR_CATALOGO", "tipo_servicio", tipo.getId(),
                "Nombre: %s -> %s".formatted(anterior, nombre));
        return aResponse(tipo);
    }

    @Transactional
    public CatalogoSimpleResponse cambiarEstadoTipoServicio(
            Long adminUsuarioId, Long id, Boolean activo) {
        validarAdministrador(adminUsuarioId);
        TipoServicio tipo = tipoServicioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tipo de servicio no encontrado"));
        boolean nuevo = validarActivo(activo);
        if (tipo.isActivo() != nuevo) {
            tipo.setActivo(nuevo);
            tipoServicioRepository.save(tipo);
            auditarEstado(adminUsuarioId, "tipo_servicio", tipo.getId(), nuevo);
        }
        return aResponse(tipo);
    }

    @Transactional
    public EspecialidadResponse crearEspecialidad(
            Long adminUsuarioId, EspecialidadRequest request) {
        validarAdministrador(adminUsuarioId);
        String nombre = normalizarObligatorio(
                request == null ? null : request.nombre(),
                MAX_NOMBRE_ESPECIALIDAD,
                "El nombre es obligatorio");
        if (especialidadRepository.existsByNombreIgnoreCase(nombre)) {
            throw new BusinessException("Ya existe una especialidad con ese nombre");
        }
        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .nombre(nombre)
                .descripcion(normalizarOpcional(
                        request == null ? null : request.descripcion(),
                        MAX_DESCRIPCION,
                        "La descripción"))
                .activo(true)
                .build());
        auditar(adminUsuarioId, "CREAR_CATALOGO", "especialidad", especialidad.getId(),
                "Nombre: " + nombre);
        return aResponse(especialidad);
    }

    @Transactional
    public EspecialidadResponse actualizarEspecialidad(
            Long adminUsuarioId, Long id, EspecialidadRequest request) {
        validarAdministrador(adminUsuarioId);
        Especialidad especialidad = especialidadRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Especialidad no encontrada"));
        String nombre = normalizarObligatorio(
                request == null ? null : request.nombre(),
                MAX_NOMBRE_ESPECIALIDAD,
                "El nombre es obligatorio");
        if (especialidadRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new BusinessException("Ya existe una especialidad con ese nombre");
        }
        String anterior = especialidad.getNombre();
        especialidad.setNombre(nombre);
        especialidad.setDescripcion(normalizarOpcional(
                request == null ? null : request.descripcion(),
                MAX_DESCRIPCION,
                "La descripción"));
        especialidadRepository.save(especialidad);
        auditar(adminUsuarioId, "ACTUALIZAR_CATALOGO", "especialidad", especialidad.getId(),
                "Nombre: %s -> %s".formatted(anterior, nombre));
        return aResponse(especialidad);
    }

    @Transactional
    public EspecialidadResponse cambiarEstadoEspecialidad(
            Long adminUsuarioId, Long id, Boolean activo) {
        validarAdministrador(adminUsuarioId);
        Especialidad especialidad = especialidadRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Especialidad no encontrada"));
        boolean nuevo = validarActivo(activo);
        if (especialidad.isActivo() != nuevo) {
            especialidad.setActivo(nuevo);
            especialidadRepository.save(especialidad);
            auditarEstado(adminUsuarioId, "especialidad", especialidad.getId(), nuevo);
        }
        return aResponse(especialidad);
    }

    @Transactional
    public DistritoResponse crearDistrito(
            Long adminUsuarioId, DistritoRequest request) {
        validarAdministrador(adminUsuarioId);
        if (request == null) {
            throw new BadRequestException("Debe indicar los datos del distrito");
        }
        String nombre = normalizarObligatorio(
                request.nombre(), MAX_NOMBRE_DISTRITO, "El nombre es obligatorio");
        String provincia = normalizarObligatorio(
                request.provincia(), MAX_PROVINCIA, "La provincia es obligatoria");
        if (distritoRepository.existsByNombreIgnoreCaseAndProvinciaIgnoreCase(
                nombre, provincia)) {
            throw new BusinessException(
                    "Ya existe un distrito con ese nombre en la provincia");
        }
        Distrito distrito = distritoRepository.save(Distrito.builder()
                .nombre(nombre)
                .provincia(provincia)
                .topeMovilidad(validarTope(request.topeMovilidad()))
                .activo(true)
                .build());
        auditar(adminUsuarioId, "CREAR_CATALOGO", "distrito", distrito.getId(),
                "Nombre: %s, provincia: %s".formatted(nombre, provincia));
        return aResponse(distrito);
    }

    @Transactional
    public DistritoResponse actualizarDistrito(
            Long adminUsuarioId, Long id, DistritoRequest request) {
        validarAdministrador(adminUsuarioId);
        if (request == null) {
            throw new BadRequestException("Debe indicar los datos del distrito");
        }
        Distrito distrito = distritoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Distrito no encontrado"));
        String nombre = normalizarObligatorio(
                request.nombre(), MAX_NOMBRE_DISTRITO, "El nombre es obligatorio");
        String provincia = normalizarObligatorio(
                request.provincia(), MAX_PROVINCIA, "La provincia es obligatoria");
        if (distritoRepository.existsByNombreIgnoreCaseAndProvinciaIgnoreCaseAndIdNot(
                nombre, provincia, id)) {
            throw new BusinessException(
                    "Ya existe un distrito con ese nombre en la provincia");
        }
        String anterior = distrito.getNombre();
        distrito.setNombre(nombre);
        distrito.setProvincia(provincia);
        distrito.setTopeMovilidad(validarTope(request.topeMovilidad()));
        distritoRepository.save(distrito);
        auditar(adminUsuarioId, "ACTUALIZAR_CATALOGO", "distrito", distrito.getId(),
                "Nombre: %s -> %s".formatted(anterior, nombre));
        return aResponse(distrito);
    }

    @Transactional
    public DistritoResponse cambiarEstadoDistrito(
            Long adminUsuarioId, Long id, Boolean activo) {
        validarAdministrador(adminUsuarioId);
        Distrito distrito = distritoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Distrito no encontrado"));
        boolean nuevo = validarActivo(activo);
        if (distrito.isActivo() != nuevo) {
            distrito.setActivo(nuevo);
            distritoRepository.save(distrito);
            auditarEstado(adminUsuarioId, "distrito", distrito.getId(), nuevo);
        }
        return aResponse(distrito);
    }

    private void validarAdministrador(Long adminUsuarioId) {
        administradorRepository.findByUsuarioId(adminUsuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de administrador"));
    }

    private String normalizarObligatorio(
            String valor, int maximo, String mensajeObligatorio) {
        if (!StringUtils.hasText(valor)) {
            throw new BadRequestException(mensajeObligatorio);
        }
        String normalizado = valor.trim();
        if (normalizado.length() > maximo) {
            throw new BadRequestException(
                    "El nombre no puede superar los %d caracteres".formatted(maximo));
        }
        return normalizado;
    }

    private String normalizarOpcional(
            String valor, int maximo, String nombre) {
        if (!StringUtils.hasText(valor)) {
            return null;
        }
        String normalizado = valor.trim();
        if (normalizado.length() > maximo) {
            throw new BadRequestException(
                    "%s no puede superar los %d caracteres"
                            .formatted(nombre, maximo));
        }
        return normalizado;
    }

    private boolean validarActivo(Boolean activo) {
        if (activo == null) {
            throw new BadRequestException(
                    "Debe indicar si el catálogo está activo");
        }
        return activo;
    }

    private BigDecimal validarTope(BigDecimal tope) {
        if (tope == null) {
            throw new BadRequestException("El tope de movilidad es obligatorio");
        }
        if (tope.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(
                    "El tope de movilidad no puede ser negativo");
        }
        if (tope.compareTo(TOPE_MOVILIDAD_MAXIMO) > 0) {
            throw new BadRequestException(
                    "El tope de movilidad no puede superar los S/ 1,000,000.00");
        }
        return tope.setScale(2, RoundingMode.HALF_UP);
    }

    private void auditarEstado(
            Long adminUsuarioId, String entidad, Long entidadId, boolean activo) {
        auditar(
                adminUsuarioId,
                "CAMBIAR_ESTADO_CATALOGO",
                entidad,
                entidadId,
                "Activo: " + activo);
    }

    private void auditar(
            Long adminUsuarioId,
            String accion,
            String entidad,
            Long entidadId,
            String detalle) {
        auditoriaService.registrar(
                adminUsuarioId, accion, entidad, entidadId, detalle);
    }

    private CatalogoSimpleResponse aResponse(TipoEquipo tipo) {
        return new CatalogoSimpleResponse(tipo.getId(), tipo.getNombre(), tipo.isActivo());
    }

    private CatalogoSimpleResponse aResponse(TipoServicio tipo) {
        return new CatalogoSimpleResponse(tipo.getId(), tipo.getNombre(), tipo.isActivo());
    }

    private EspecialidadResponse aResponse(Especialidad especialidad) {
        return new EspecialidadResponse(
                especialidad.getId(),
                especialidad.getNombre(),
                especialidad.getDescripcion(),
                especialidad.isActivo());
    }

    private DistritoResponse aResponse(Distrito distrito) {
        return new DistritoResponse(
                distrito.getId(),
                distrito.getNombre(),
                distrito.getProvincia(),
                distrito.getTopeMovilidad(),
                distrito.isActivo());
    }
}
