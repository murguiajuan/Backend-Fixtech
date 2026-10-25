package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fixtech.dto.CatalogoSimpleRequest;
import com.fixtech.dto.CatalogoSimpleResponse;
import com.fixtech.dto.DistritoRequest;
import com.fixtech.dto.DistritoResponse;
import com.fixtech.dto.EspecialidadRequest;
import com.fixtech.dto.EspecialidadResponse;
import com.fixtech.entity.Administrador;
import com.fixtech.entity.Distrito;
import com.fixtech.entity.Especialidad;
import com.fixtech.entity.TipoEquipo;
import com.fixtech.entity.TipoServicio;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.Rol;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock
    private TipoEquipoRepository tipoEquipoRepository;

    @Mock
    private TipoServicioRepository tipoServicioRepository;

    @Mock
    private EspecialidadRepository especialidadRepository;

    @Mock
    private DistritoRepository distritoRepository;

    @Mock
    private AdministradorRepository administradorRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private CatalogoService catalogoService;

    @Test
    void lista_los_catalogos_activos_y_todos_para_administracion() {
        when(tipoEquipoRepository.findByActivoTrueOrderByNombreAsc())
                .thenReturn(List.of(tipoEquipo(1L, "Laptop", true)));
        when(distritoRepository.findByActivoTrueOrderByNombreAsc())
                .thenReturn(List.of(distrito(4L, "Piura", "Piura", "10.00", true)));
        when(tipoServicioRepository.findAllByOrderByNombreAsc())
                .thenReturn(List.of(
                        tipoServicio(2L, "Mantenimiento", true),
                        tipoServicio(6L, "Reparación", false)));
        when(especialidadRepository.findAllByOrderByNombreAsc())
                .thenReturn(List.of(
                        especialidad(3L, "Redes", "Configuración", true),
                        especialidad(7L, "Hardware", null, false)));

        List<CatalogoSimpleResponse> equipos = catalogoService
                .listarTiposEquipo(true);
        List<CatalogoSimpleResponse> servicios = catalogoService
                .listarTiposServicio(false);
        List<EspecialidadResponse> especialidades = catalogoService
                .listarEspecialidades(false);
        List<DistritoResponse> distritos = catalogoService
                .listarDistritos(true);

        assertEquals(1, equipos.size());
        assertEquals("Laptop", equipos.get(0).nombre());
        assertEquals(2, servicios.size());
        assertFalse(servicios.get(1).activo());
        assertEquals(2, especialidades.size());
        assertNull(especialidades.get(1).descripcion());
        assertEquals(1, distritos.size());
        assertEquals(new BigDecimal("10.00"), distritos.get(0).topeMovilidad());
    }

    @Test
    void crea_y_actualiza_tipos_de_equipo() {
        conAdministrador();
        when(tipoEquipoRepository.existsByNombreIgnoreCase("Laptop")).thenReturn(false);
        when(tipoEquipoRepository.save(any(TipoEquipo.class)))
                .thenAnswer(invocacion -> {
                    TipoEquipo tipo = invocacion.getArgument(0);
                    tipo.setId(10L);
                    return tipo;
                });
        TipoEquipo existente = tipoEquipo(10L, "Laptop", true);
        when(tipoEquipoRepository.findById(10L)).thenReturn(Optional.of(existente));
        when(tipoEquipoRepository.existsByNombreIgnoreCaseAndIdNot("Laptop Pro", 10L))
                .thenReturn(false);

        CatalogoSimpleResponse creado = catalogoService.crearTipoEquipo(
                1L, new CatalogoSimpleRequest("  Laptop  "));
        CatalogoSimpleResponse actualizado = catalogoService.actualizarTipoEquipo(
                1L, 10L, new CatalogoSimpleRequest(" Laptop Pro "));

        assertEquals(10L, creado.id());
        assertEquals("Laptop", creado.nombre());
        assertTrue(creado.activo());
        assertEquals("Laptop Pro", actualizado.nombre());
        verify(auditoriaService).registrar(
                1L, "CREAR_CATALOGO", "tipo_equipo", 10L, "Nombre: Laptop");
        verify(auditoriaService).registrar(
                1L, "ACTUALIZAR_CATALOGO", "tipo_equipo", 10L,
                "Nombre: Laptop -> Laptop Pro");
    }

    @Test
    void valida_las_operaciones_sobre_tipos_de_equipo() {
        conAdministrador();
        when(tipoEquipoRepository.existsByNombreIgnoreCase("Laptop")).thenReturn(true);
        assertThrows(BusinessException.class,
                () -> catalogoService.crearTipoEquipo(
                        1L, new CatalogoSimpleRequest("Laptop")));
        assertThrows(BadRequestException.class,
                () -> catalogoService.crearTipoEquipo(
                        1L, new CatalogoSimpleRequest("   ")));
        assertThrows(BadRequestException.class,
                () -> catalogoService.crearTipoEquipo(
                        1L, new CatalogoSimpleRequest("x".repeat(61))));

        when(tipoEquipoRepository.findById(10L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
                () -> catalogoService.actualizarTipoEquipo(
                        1L, 10L, new CatalogoSimpleRequest("Otro")));
        assertThrows(NotFoundException.class,
                () -> catalogoService.cambiarEstadoTipoEquipo(1L, 10L, true));

        TipoEquipo existente = tipoEquipo(10L, "Laptop", true);
        when(tipoEquipoRepository.findById(11L)).thenReturn(Optional.of(existente));
        when(tipoEquipoRepository.existsByNombreIgnoreCaseAndIdNot("Laptop", 11L))
                .thenReturn(true);
        assertThrows(BusinessException.class,
                () -> catalogoService.actualizarTipoEquipo(
                        1L, 11L, new CatalogoSimpleRequest("Laptop")));
        assertThrows(BadRequestException.class,
                () -> catalogoService.cambiarEstadoTipoEquipo(1L, 11L, null));

        CatalogoSimpleResponse sinCambio = catalogoService
                .cambiarEstadoTipoEquipo(1L, 11L, true);
        assertTrue(sinCambio.activo());
        verify(tipoEquipoRepository, never()).save(existente);
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void crea_actualiza_y_cambia_el_estado_de_tipos_de_servicio() {
        conAdministrador();
        when(tipoServicioRepository.existsByNombreIgnoreCase("Mantenimiento"))
                .thenReturn(false);
        when(tipoServicioRepository.save(any(TipoServicio.class)))
                .thenAnswer(invocacion -> {
                    TipoServicio tipo = invocacion.getArgument(0);
                    tipo.setId(20L);
                    return tipo;
                });
        TipoServicio existente = tipoServicio(20L, "Mantenimiento", true);
        when(tipoServicioRepository.findById(20L)).thenReturn(Optional.of(existente));
        when(tipoServicioRepository.existsByNombreIgnoreCaseAndIdNot(
                "Mantenimiento preventivo", 20L)).thenReturn(false);

        CatalogoSimpleResponse creado = catalogoService.crearTipoServicio(
                1L, new CatalogoSimpleRequest("Mantenimiento"));
        CatalogoSimpleResponse actualizado = catalogoService.actualizarTipoServicio(
                1L, 20L, new CatalogoSimpleRequest("Mantenimiento preventivo"));
        CatalogoSimpleResponse desactivado = catalogoService
                .cambiarEstadoTipoServicio(1L, 20L, false);

        assertEquals("Mantenimiento", creado.nombre());
        assertEquals("Mantenimiento preventivo", actualizado.nombre());
        assertFalse(desactivado.activo());
        verify(auditoriaService).registrar(
                1L, "CREAR_CATALOGO", "tipo_servicio", 20L,
                "Nombre: Mantenimiento");
        verify(auditoriaService).registrar(
                1L, "CAMBIAR_ESTADO_CATALOGO", "tipo_servicio", 20L,
                "Activo: false");
    }

    @Test
    void valida_las_operaciones_sobre_tipos_de_servicio() {
        conAdministrador();
        when(tipoServicioRepository.existsByNombreIgnoreCase("Mantenimiento"))
                .thenReturn(true);
        assertThrows(BusinessException.class,
                () -> catalogoService.crearTipoServicio(
                        1L, new CatalogoSimpleRequest("Mantenimiento")));
        assertThrows(BadRequestException.class,
                () -> catalogoService.crearTipoServicio(
                        1L, new CatalogoSimpleRequest("x".repeat(81))));
        when(tipoServicioRepository.findById(20L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
                () -> catalogoService.actualizarTipoServicio(
                        1L, 20L, new CatalogoSimpleRequest("Otro")));
        assertThrows(NotFoundException.class,
                () -> catalogoService.cambiarEstadoTipoServicio(1L, 20L, true));

        TipoServicio existente = tipoServicio(21L, "Mantenimiento", true);
        when(tipoServicioRepository.findById(21L)).thenReturn(Optional.of(existente));
        when(tipoServicioRepository.existsByNombreIgnoreCaseAndIdNot(
                "Mantenimiento", 21L)).thenReturn(true);
        assertThrows(BusinessException.class,
                () -> catalogoService.actualizarTipoServicio(
                        1L, 21L, new CatalogoSimpleRequest("Mantenimiento")));
        assertThrows(BadRequestException.class,
                () -> catalogoService.cambiarEstadoTipoServicio(1L, 21L, null));
        verify(tipoServicioRepository, never()).save(existente);
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void crea_actualiza_y_cambia_el_estado_de_especialidades() {
        conAdministrador();
        when(especialidadRepository.existsByNombreIgnoreCase("Redes"))
                .thenReturn(false);
        when(especialidadRepository.save(any(Especialidad.class)))
                .thenAnswer(invocacion -> {
                    Especialidad especialidad = invocacion.getArgument(0);
                    especialidad.setId(30L);
                    return especialidad;
                });
        Especialidad existente = especialidad(30L, "Redes", "Configuración", true);
        when(especialidadRepository.findById(30L)).thenReturn(Optional.of(existente));
        when(especialidadRepository.existsByNombreIgnoreCaseAndIdNot(
                "Redes y cableado", 30L)).thenReturn(false);

        EspecialidadResponse creada = catalogoService.crearEspecialidad(
                1L, new EspecialidadRequest(" Redes ", " Instalación "));
        EspecialidadResponse actualizada = catalogoService.actualizarEspecialidad(
                1L, 30L, new EspecialidadRequest("Redes y cableado", "  "));
        EspecialidadResponse desactivada = catalogoService
                .cambiarEstadoEspecialidad(1L, 30L, false);

        assertEquals("Redes", creada.nombre());
        assertEquals("Instalación", creada.descripcion());
        assertEquals("Redes y cableado", actualizada.nombre());
        assertNull(actualizada.descripcion());
        assertFalse(desactivada.activo());
        verify(auditoriaService).registrar(
                1L, "CREAR_CATALOGO", "especialidad", 30L, "Nombre: Redes");
        verify(auditoriaService).registrar(
                1L, "CAMBIAR_ESTADO_CATALOGO", "especialidad", 30L,
                "Activo: false");
    }

    @Test
    void valida_las_operaciones_sobre_especialidades() {
        conAdministrador();
        when(especialidadRepository.existsByNombreIgnoreCase("Redes"))
                .thenReturn(true);
        assertThrows(BusinessException.class,
                () -> catalogoService.crearEspecialidad(
                        1L, new EspecialidadRequest("Redes", null)));
        assertThrows(BadRequestException.class,
                () -> catalogoService.crearEspecialidad(
                        1L, new EspecialidadRequest("x".repeat(81), null)));
        assertThrows(BadRequestException.class,
                () -> catalogoService.crearEspecialidad(
                        1L, new EspecialidadRequest("Hardware", "x".repeat(201))));
        when(especialidadRepository.findById(30L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
                () -> catalogoService.actualizarEspecialidad(
                        1L, 30L, new EspecialidadRequest("Otra", null)));
        assertThrows(NotFoundException.class,
                () -> catalogoService.cambiarEstadoEspecialidad(1L, 30L, true));

        Especialidad existente = especialidad(31L, "Redes", null, true);
        when(especialidadRepository.findById(31L)).thenReturn(Optional.of(existente));
        when(especialidadRepository.existsByNombreIgnoreCaseAndIdNot(
                "Cableado", 31L)).thenReturn(false);
        assertThrows(BadRequestException.class,
                () -> catalogoService.actualizarEspecialidad(1L, 31L, null));
        assertThrows(BadRequestException.class,
                () -> catalogoService.actualizarEspecialidad(
                        1L, 31L, new EspecialidadRequest("Cableado", "x".repeat(201))));
        assertThrows(BadRequestException.class,
                () -> catalogoService.cambiarEstadoEspecialidad(1L, 31L, null));
        verify(especialidadRepository, never()).save(existente);
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void crea_actualiza_y_cambia_el_estado_de_distritos() {
        conAdministrador();
        when(distritoRepository.existsByNombreIgnoreCaseAndProvinciaIgnoreCase(
                "Piura Centro", "Piura")).thenReturn(false);
        when(distritoRepository.save(any(Distrito.class)))
                .thenAnswer(invocacion -> {
                    Distrito distrito = invocacion.getArgument(0);
                    distrito.setId(40L);
                    return distrito;
                });
        Distrito existente = distrito(40L, "Piura Centro", "Piura", "5.00", true);
        when(distritoRepository.findById(40L)).thenReturn(Optional.of(existente));
        when(distritoRepository.existsByNombreIgnoreCaseAndProvinciaIgnoreCaseAndIdNot(
                "Castilla", "Piura", 40L)).thenReturn(false);

        DistritoResponse creado = catalogoService.crearDistrito(
                1L,
                new DistritoRequest(
                        "  Piura Centro ", " Piura ", new BigDecimal("5.5")));
        DistritoResponse actualizado = catalogoService.actualizarDistrito(
                1L,
                40L,
                new DistritoRequest("Castilla", "Piura", new BigDecimal("8")));
        DistritoResponse desactivado = catalogoService
                .cambiarEstadoDistrito(1L, 40L, false);

        assertEquals("Piura Centro", creado.nombre());
        assertEquals(new BigDecimal("5.50"), creado.topeMovilidad());
        assertEquals("Castilla", actualizado.nombre());
        assertEquals(new BigDecimal("8.00"), actualizado.topeMovilidad());
        assertFalse(desactivado.activo());
        verify(auditoriaService).registrar(
                1L, "CREAR_CATALOGO", "distrito", 40L,
                "Nombre: Piura Centro, provincia: Piura");
        verify(auditoriaService).registrar(
                1L, "CAMBIAR_ESTADO_CATALOGO", "distrito", 40L,
                "Activo: false");
    }

    @Test
    void valida_las_operaciones_sobre_distritos() {
        conAdministrador();
        when(distritoRepository.existsByNombreIgnoreCaseAndProvinciaIgnoreCase(
                "Piura", "Piura")).thenReturn(true);
        assertThrows(BusinessException.class,
                () -> catalogoService.crearDistrito(
                        1L,
                        new DistritoRequest("Piura", "Piura", new BigDecimal("5"))));
        assertThrows(BadRequestException.class,
                () -> catalogoService.crearDistrito(1L, null));
        assertThrows(BadRequestException.class,
                () -> catalogoService.crearDistrito(
                        1L,
                        new DistritoRequest(" ", "Piura", new BigDecimal("5"))));
        assertThrows(BadRequestException.class,
                () -> catalogoService.crearDistrito(
                        1L,
                        new DistritoRequest("Piura", " ", new BigDecimal("5"))));
        assertThrows(BadRequestException.class,
                () -> catalogoService.crearDistrito(
                        1L,
                        new DistritoRequest("Castilla", "Piura", null)));
        assertThrows(BadRequestException.class,
                () -> catalogoService.crearDistrito(
                        1L,
                        new DistritoRequest(
                                "Castilla", "Piura", new BigDecimal("-1"))));
        assertThrows(BadRequestException.class,
                () -> catalogoService.crearDistrito(
                        1L,
                        new DistritoRequest(
                                "Castilla", "Piura", new BigDecimal("1000000.01"))));

        when(distritoRepository.findById(40L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
                () -> catalogoService.actualizarDistrito(
                        1L,
                        40L,
                        new DistritoRequest("Castilla", "Piura", BigDecimal.ONE)));
        assertThrows(NotFoundException.class,
                () -> catalogoService.cambiarEstadoDistrito(1L, 40L, true));

        Distrito existente = distrito(41L, "Piura", "Piura", "5.00", true);
        when(distritoRepository.findById(41L)).thenReturn(Optional.of(existente));
        when(distritoRepository.existsByNombreIgnoreCaseAndProvinciaIgnoreCaseAndIdNot(
                "Piura", "Piura", 41L)).thenReturn(true);
        assertThrows(BusinessException.class,
                () -> catalogoService.actualizarDistrito(
                        1L,
                        41L,
                        new DistritoRequest("Piura", "Piura", BigDecimal.ONE)));
        assertThrows(BadRequestException.class,
                () -> catalogoService.actualizarDistrito(1L, 41L, null));
        assertThrows(BadRequestException.class,
                () -> catalogoService.cambiarEstadoDistrito(1L, 41L, null));
        verify(distritoRepository, never()).save(existente);
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void exige_perfil_de_administrador_para_modificar_catalogos() {
        when(administradorRepository.findByUsuarioId(9L))
                .thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> catalogoService.crearTipoEquipo(
                        9L, new CatalogoSimpleRequest("Laptop")));
        assertThrows(ForbiddenException.class,
                () -> catalogoService.cambiarEstadoDistrito(9L, 1L, false));
        verifyNoInteractions(tipoEquipoRepository, distritoRepository);
        verifyNoInteractions(auditoriaService);
    }

    private void conAdministrador() {
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(Administrador.builder()
                        .id(1L)
                        .usuario(Usuario.builder().id(1L).rol(Rol.ADMIN).build())
                        .nombres("Admin")
                        .apellidos("Prueba")
                        .build()));
    }

    private TipoEquipo tipoEquipo(Long id, String nombre, boolean activo) {
        return TipoEquipo.builder()
                .id(id)
                .nombre(nombre)
                .activo(activo)
                .build();
    }

    private TipoServicio tipoServicio(Long id, String nombre, boolean activo) {
        return TipoServicio.builder()
                .id(id)
                .nombre(nombre)
                .activo(activo)
                .build();
    }

    private Especialidad especialidad(
            Long id, String nombre, String descripcion, boolean activo) {
        return Especialidad.builder()
                .id(id)
                .nombre(nombre)
                .descripcion(descripcion)
                .activo(activo)
                .build();
    }

    private Distrito distrito(
            Long id, String nombre, String provincia, String tope, boolean activo) {
        return Distrito.builder()
                .id(id)
                .nombre(nombre)
                .provincia(provincia)
                .topeMovilidad(new BigDecimal(tope))
                .activo(activo)
                .build();
    }
}
