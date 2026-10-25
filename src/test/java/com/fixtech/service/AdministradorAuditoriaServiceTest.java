package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.fixtech.dto.PaginaAuditoriaResponse;
import com.fixtech.entity.Administrador;
import com.fixtech.entity.Auditoria;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.Rol;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.AuditoriaRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AdministradorAuditoriaServiceTest {

    @Mock
    private AuditoriaRepository auditoriaRepository;

    @Mock
    private AdministradorRepository administradorRepository;

    @InjectMocks
    private AdministradorAuditoriaService administradorAuditoriaService;

    @Test
    void lista_la_auditoria_con_filtros_y_paginacion() {
        conAdministrador();
        Auditoria auditoria = Auditoria.builder()
                .id(10L)
                .usuarioId(1L)
                .accion("SUSPENDER_USUARIO")
                .entidad("usuario")
                .entidadId(2L)
                .detalle("Motivo: prueba")
                .ip("127.0.0.1")
                .creadoEn(LocalDateTime.of(2026, 10, 20, 10, 30))
                .build();
        Page<Auditoria> pagina = new PageImpl<>(
                List.of(auditoria), PageRequest.of(1, 10), 25);
        when(auditoriaRepository.buscar(
                eq(1L),
                eq("usuario"),
                eq("SUSPENDER_USUARIO"),
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 26, 0, 0)),
                any(Pageable.class))).thenReturn(pagina);

        PaginaAuditoriaResponse respuesta = administradorAuditoriaService.listar(
                1L,
                1L,
                " usuario ",
                " suspender_usuario ",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 25),
                1,
                10);

        assertEquals(1, respuesta.contenido().size());
        assertEquals("SUSPENDER_USUARIO", respuesta.contenido().get(0).accion());
        assertEquals("127.0.0.1", respuesta.contenido().get(0).ip());
        assertEquals(1, respuesta.pagina());
        assertEquals(10, respuesta.tamano());
        assertEquals(25, respuesta.totalElementos());
        assertEquals(3, respuesta.totalPaginas());
    }

    @Test
    void lista_sin_filtros_cuando_no_se_especifican() {
        conAdministrador();
        when(auditoriaRepository.buscar(
                isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        PaginaAuditoriaResponse respuesta = administradorAuditoriaService.listar(
                1L, null, "   ", null, null, null, 0, 20);

        assertEquals(0, respuesta.contenido().size());
        assertEquals(0, respuesta.totalElementos());
        assertEquals(0, respuesta.totalPaginas());
    }

    @Test
    void valida_los_parametros_de_consulta() {
        conAdministrador();

        assertThrows(BadRequestException.class,
                () -> administradorAuditoriaService.listar(
                        1L, null, null, null, null, null, -1, 20));
        assertThrows(BadRequestException.class,
                () -> administradorAuditoriaService.listar(
                        1L, null, null, null, null, null, 0, 0));
        assertThrows(BadRequestException.class,
                () -> administradorAuditoriaService.listar(
                        1L, null, null, null, null, null, 0, 101));
        assertThrows(BadRequestException.class,
                () -> administradorAuditoriaService.listar(
                        1L, null, "x".repeat(61), null, null, null, 0, 20));
        assertThrows(BadRequestException.class,
                () -> administradorAuditoriaService.listar(
                        1L, null, null, "x".repeat(61), null, null, 0, 20));
        assertThrows(BadRequestException.class,
                () -> administradorAuditoriaService.listar(
                        1L, null, null, null,
                        LocalDate.of(2026, 10, 25),
                        LocalDate.of(2026, 10, 1),
                        0, 20));
    }

    @Test
    void exige_perfil_de_administrador() {
        when(administradorRepository.findByUsuarioId(9L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> administradorAuditoriaService.listar(
                        9L, null, null, null, null, null, 0, 20));
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
}
