package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fixtech.dto.PerfilTecnicoRequest;
import com.fixtech.dto.PerfilUsuarioResponse;
import com.fixtech.entity.Distrito;
import com.fixtech.entity.Especialidad;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.Rol;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.DistritoRepository;
import com.fixtech.repository.EspecialidadRepository;
import com.fixtech.repository.TecnicoRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class PerfilTecnicoServiceTest {

    @Mock
    private TecnicoRepository tecnicoRepository;

    @Mock
    private EspecialidadRepository especialidadRepository;

    @Mock
    private DistritoRepository distritoRepository;

    @Mock
    private PerfilUsuarioService perfilUsuarioService;

    @Mock
    private ArchivoService archivoService;

    @InjectMocks
    private PerfilTecnicoService perfilTecnicoService;

    @Test
    void actualiza_el_perfil_y_reinicia_la_verificacion() {
        Tecnico tecnico = tecnico();
        when(tecnicoRepository.findByUsuarioId(7L)).thenReturn(Optional.of(tecnico));
        when(tecnicoRepository.existsByDniAndIdNot("87654321", 20L)).thenReturn(false);
        when(especialidadRepository.findAllById(Set.of(1L)))
                .thenReturn(List.of(especialidad(1L, "Redes y WiFi")));
        when(distritoRepository.findAllById(Set.of(2L)))
                .thenReturn(List.of(distrito(2L, "Castilla")));
        when(tecnicoRepository.save(tecnico)).thenReturn(tecnico);
        when(perfilUsuarioService.obtener(7L)).thenReturn(perfil());

        PerfilUsuarioResponse response = perfilTecnicoService.actualizar(7L, request());

        assertEquals("Carlos", tecnico.getNombres());
        assertEquals("Mena", tecnico.getApellidos());
        assertEquals("87654321", tecnico.getDni());
        assertEquals("988888888", tecnico.getCelular());
        assertEquals("Especialista en redes", tecnico.getDescripcion());
        assertEquals(12, Byte.toUnsignedInt(tecnico.getAniosExperiencia()));
        assertTrue(tecnico.isOfreceDomicilio());
        assertTrue(tecnico.isOfreceTaller());
        assertEquals("Av. Grau 456", tecnico.getDireccionTaller());
        assertEquals("Frente al parque", tecnico.getReferenciaTaller());
        assertEquals("Lunes a viernes", tecnico.getHorarioTaller());
        assertEquals(10, tecnico.getGarantiaDiasDefecto());
        assertEquals(1, tecnico.getEspecialidades().size());
        assertEquals(1, tecnico.getDistritos().size());
        assertEquals(EstadoVerificacion.PENDIENTE, tecnico.getEstadoVerificacion());
        assertNull(tecnico.getMotivoVerificacion());
        assertNull(tecnico.getVerificadoPorId());
        assertNull(tecnico.getFechaVerificacion());
        verify(tecnicoRepository).save(tecnico);
        assertEquals(perfil(), response);
    }

    @Test
    void rechaza_un_perfil_sin_modalidades() {
        when(tecnicoRepository.findByUsuarioId(7L)).thenReturn(Optional.of(tecnico()));
        PerfilTecnicoRequest request = new PerfilTecnicoRequest(
                "Carlos", "Mena", "12345678", "999999999", null, 5,
                false, false, null, null, null, 7, Set.of(1L), Set.of(2L));

        assertThrows(BusinessException.class,
                () -> perfilTecnicoService.actualizar(7L, request));

        verify(tecnicoRepository, never()).save(any(Tecnico.class));
        verifyNoInteractions(especialidadRepository, distritoRepository);
    }

    @Test
    void rechaza_taller_sin_direccion() {
        when(tecnicoRepository.findByUsuarioId(7L)).thenReturn(Optional.of(tecnico()));
        PerfilTecnicoRequest request = new PerfilTecnicoRequest(
                "Carlos", "Mena", "12345678", "999999999", null, 5,
                true, true, " ", null, null, 7, Set.of(1L), Set.of(2L));

        assertThrows(BusinessException.class,
                () -> perfilTecnicoService.actualizar(7L, request));

        verify(tecnicoRepository, never()).save(any(Tecnico.class));
    }

    @Test
    void rechaza_un_dni_registrado_por_otro_tecnico() {
        when(tecnicoRepository.findByUsuarioId(7L)).thenReturn(Optional.of(tecnico()));
        when(tecnicoRepository.existsByDniAndIdNot("87654321", 20L)).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> perfilTecnicoService.actualizar(7L, request()));

        verify(tecnicoRepository, never()).save(any(Tecnico.class));
        verifyNoInteractions(especialidadRepository, distritoRepository);
    }

    @Test
    void rechaza_especialidades_o_distritos_inactivos() {
        when(tecnicoRepository.findByUsuarioId(7L)).thenReturn(Optional.of(tecnico()));
        when(tecnicoRepository.existsByDniAndIdNot("87654321", 20L)).thenReturn(false);
        when(especialidadRepository.findAllById(Set.of(1L)))
                .thenReturn(List.of(especialidadInactiva()));

        assertThrows(BusinessException.class,
                () -> perfilTecnicoService.actualizar(7L, request()));

        verify(tecnicoRepository, never()).save(any(Tecnico.class));
        verifyNoInteractions(distritoRepository);
    }

    @Test
    void rechaza_una_cuenta_sin_perfil_tecnico() {
        when(tecnicoRepository.findByUsuarioId(7L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> perfilTecnicoService.actualizar(7L, request()));

        verifyNoInteractions(especialidadRepository, distritoRepository,
                archivoService, perfilUsuarioService);
    }

    @Test
    void actualiza_la_foto_y_elimina_la_anterior() {
        Tecnico tecnico = tecnico();
        tecnico.setFotoUrl("tecnicos/20/perfil/anterior.png");
        MockMultipartFile foto = new MockMultipartFile(
                "foto", "nueva.png", "image/png", new byte[]{1, 2, 3});
        when(tecnicoRepository.findByUsuarioId(7L)).thenReturn(Optional.of(tecnico));
        when(archivoService.guardarFotoPerfilTecnico(20L, foto))
                .thenReturn("tecnicos/20/perfil/nueva.png");
        when(perfilUsuarioService.obtener(7L)).thenReturn(perfil());

        perfilTecnicoService.actualizarFoto(7L, foto);

        assertEquals("tecnicos/20/perfil/nueva.png", tecnico.getFotoUrl());
        verify(tecnicoRepository).save(tecnico);
        verify(archivoService).eliminar("tecnicos/20/perfil/anterior.png");
    }

    @Test
    void rechaza_la_foto_de_un_tecnico_sin_imagen() {
        when(tecnicoRepository.findById(20L)).thenReturn(Optional.of(tecnico()));

        assertThrows(NotFoundException.class,
                () -> perfilTecnicoService.obtenerFoto(20L));

        verifyNoInteractions(archivoService);
    }

    @Test
    void obtiene_la_foto_de_un_tecnico() {
        Tecnico tecnico = tecnico();
        tecnico.setFotoUrl("tecnicos/20/perfil/foto.png");
        ArchivoService.RecursoImagen imagen = new ArchivoService.RecursoImagen(
                new org.springframework.core.io.ByteArrayResource(new byte[]{1}),
                org.springframework.http.MediaType.IMAGE_PNG);
        when(tecnicoRepository.findById(20L)).thenReturn(Optional.of(tecnico));
        when(archivoService.cargar("tecnicos/20/perfil/foto.png")).thenReturn(imagen);

        ArchivoService.RecursoImagen response = perfilTecnicoService.obtenerFoto(20L);

        assertEquals(imagen, response);
    }

    private PerfilTecnicoRequest request() {
        return new PerfilTecnicoRequest(
                " Carlos ", " Mena ", "87654321", "988888888",
                " Especialista en redes ", 12, true, true,
                " Av. Grau 456 ", " Frente al parque ", " Lunes a viernes ",
                10, Set.of(1L), Set.of(2L));
    }

    private Tecnico tecnico() {
        return Tecnico.builder()
                .id(20L)
                .usuario(Usuario.builder()
                        .id(7L)
                        .correo("tecnico@fixtech.pe")
                        .rol(Rol.TECNICO)
                        .estado(EstadoUsuario.ACTIVO)
                        .build())
                .nombres("Anterior")
                .apellidos("Perfil")
                .dni("12345678")
                .celular("999999999")
                .estadoVerificacion(EstadoVerificacion.APROBADO)
                .motivoVerificacion("Revisado")
                .verificadoPorId(1L)
                .fechaVerificacion(LocalDateTime.now())
                .build();
    }

    private PerfilUsuarioResponse perfil() {
        return new PerfilUsuarioResponse(
                7L, "tecnico@fixtech.pe", Rol.TECNICO, EstadoUsuario.ACTIVO,
                "Carlos", "Mena", null, null);
    }

    private Especialidad especialidad(Long id, String nombre) {
        return Especialidad.builder()
                .id(id)
                .nombre(nombre)
                .activo(true)
                .build();
    }

    private Especialidad especialidadInactiva() {
        return Especialidad.builder()
                .id(1L)
                .nombre("Inactiva")
                .activo(false)
                .build();
    }

    private Distrito distrito(Long id, String nombre) {
        return Distrito.builder()
                .id(id)
                .nombre(nombre)
                .provincia("Piura")
                .topeMovilidad(new BigDecimal("15.00"))
                .activo(true)
                .build();
    }
}
