package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.ModeracionResenaRequest;
import com.fixtech.dto.ReporteResenaRequest;
import com.fixtech.dto.ResenaRequest;
import com.fixtech.dto.ResenaResponse;
import com.fixtech.dto.RespuestaResenaRequest;
import com.fixtech.entity.Administrador;
import com.fixtech.entity.Resena;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.enums.EstadoResena;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.mapper.ResenaMapper;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ResenaRepository;
import com.fixtech.repository.ServicioRepository;
import com.fixtech.repository.TecnicoRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResenaServiceTest {

    @Mock
    private ResenaRepository resenaRepository;

    @Mock
    private TecnicoRepository tecnicoRepository;

    @Mock
    private ServicioRepository servicioRepository;

    @Mock
    private AdministradorRepository administradorRepository;

    @Mock
    private ServicioAccesoService servicioAccesoService;

    @Mock
    private ServicioEventoService servicioEventoService;

    @Mock
    private ParametroSistemaService parametroSistemaService;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private AuditoriaService auditoriaService;

    @Spy
    private ResenaMapper resenaMapper = new ResenaMapper();

    @InjectMocks
    private ResenaService resenaService;

    @Test
    void registra_una_resena_y_actualiza_el_promedio_del_tecnico() {
        Servicio servicio = servicio(EstadoServicio.CONFIRMADO);
        Tecnico tecnico = servicio.getTecnico();
        prepararRegistro(servicio);
        when(resenaRepository.countByTecnicoIdAndEstado(
                tecnico.getId(), EstadoResena.VISIBLE)).thenReturn(4L);
        when(resenaRepository.promedioEstrellas(
                tecnico.getId(), EstadoResena.VISIBLE)).thenReturn(4.5);

        ResenaResponse response = resenaService.registrar(
                10L,
                servicio.getId(),
                new ResenaRequest(5, 5, 4, 5, 4, " Excelente servicio "));

        assertEquals(5, response.estrellas());
        assertEquals("Excelente servicio", response.comentario());
        assertEquals(EstadoResena.VISIBLE, response.estado());
        assertEquals("Tecnico Prueba", response.tecnicoNombre());
        assertEquals("Cliente P.", response.clienteNombre());
        assertEquals(EstadoServicio.CALIFICADO, servicio.getEstado());
        assertEquals(4, tecnico.getTotalResenas());
        assertEquals(new BigDecimal("4.50"), tecnico.getCalificacionPromedio());
        verify(servicioRepository).save(servicio);
        verify(servicioEventoService).registrarCambio(
                servicio,
                EstadoServicio.CONFIRMADO,
                EstadoServicio.CALIFICADO,
                10L,
                "Reseña registrada por el cliente");
        verify(notificacionService).crear(
                eq(20L),
                eq(TipoNotificacion.RESENA_REGISTRADA),
                anyString(),
                contains("5 estrella"),
                eq("RESENA"),
                any());
    }

    @Test
    void redondea_el_promedio_con_half_up() {
        Servicio servicio = servicio(EstadoServicio.CONFIRMADO);
        Tecnico tecnico = servicio.getTecnico();
        prepararRegistro(servicio);
        when(resenaRepository.countByTecnicoIdAndEstado(
                tecnico.getId(), EstadoResena.VISIBLE)).thenReturn(3L);
        when(resenaRepository.promedioEstrellas(
                tecnico.getId(), EstadoResena.VISIBLE)).thenReturn(4.666);

        resenaService.registrar(
                10L,
                servicio.getId(),
                new ResenaRequest(5, null, null, null, null, null));

        assertEquals(new BigDecimal("4.67"), tecnico.getCalificacionPromedio());
    }

    @Test
    void rechaza_la_resena_si_el_usuario_no_es_el_cliente() {
        Servicio servicio = servicio(EstadoServicio.CONFIRMADO);
        when(servicioAccesoService.bloquear(servicio.getId())).thenReturn(servicio);
        doThrow(new ForbiddenException("Solo el cliente"))
                .when(servicioAccesoService).validarCliente(servicio, 99L);

        assertThrows(
                ForbiddenException.class,
                () -> resenaService.registrar(
                        99L,
                        servicio.getId(),
                        new ResenaRequest(5, null, null, null, null, null)));

        verify(resenaRepository, never()).save(any());
    }

    @Test
    void rechaza_una_segunda_resena_del_mismo_servicio() {
        Servicio servicio = servicio(EstadoServicio.CONFIRMADO);
        when(servicioAccesoService.bloquear(servicio.getId())).thenReturn(servicio);
        when(resenaRepository.existsByServicioId(servicio.getId())).thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> resenaService.registrar(
                        10L,
                        servicio.getId(),
                        new ResenaRequest(5, null, null, null, null, null)));

        verify(parametroSistemaService, never())
                .obtenerEntero(ParametroClave.DIAS_PARA_RESENAR);
    }

    @Test
    void rechaza_la_resena_si_el_servicio_no_esta_confirmado() {
        Servicio servicio = servicio(EstadoServicio.FINALIZADO);
        when(servicioAccesoService.bloquear(servicio.getId())).thenReturn(servicio);
        when(resenaRepository.existsByServicioId(servicio.getId())).thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> resenaService.registrar(
                        10L,
                        servicio.getId(),
                        new ResenaRequest(5, null, null, null, null, null)));
    }

    @Test
    void rechaza_la_resena_cuando_vencio_el_plazo() {
        Servicio servicio = servicio(EstadoServicio.CONFIRMADO);
        servicio.setFechaConfirmacion(LocalDateTime.now().minusDays(16));
        when(servicioAccesoService.bloquear(servicio.getId())).thenReturn(servicio);
        when(resenaRepository.existsByServicioId(servicio.getId())).thenReturn(false);
        when(parametroSistemaService.obtenerEntero(ParametroClave.DIAS_PARA_RESENAR))
                .thenReturn(15);

        assertThrows(
                BusinessException.class,
                () -> resenaService.registrar(
                        10L,
                        servicio.getId(),
                        new ResenaRequest(5, null, null, null, null, null)));
    }

    @Test
    void rechaza_la_resena_si_no_hay_fecha_de_confirmacion() {
        Servicio servicio = servicio(EstadoServicio.CONFIRMADO);
        servicio.setFechaConfirmacion(null);
        when(servicioAccesoService.bloquear(servicio.getId())).thenReturn(servicio);
        when(resenaRepository.existsByServicioId(servicio.getId())).thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> resenaService.registrar(
                        10L,
                        servicio.getId(),
                        new ResenaRequest(5, null, null, null, null, null)));
    }

    @Test
    void rechaza_calificaciones_y_comentarios_invalidos() {
        Servicio servicio = servicio(EstadoServicio.CONFIRMADO);
        when(servicioAccesoService.bloquear(servicio.getId())).thenReturn(servicio);
        when(resenaRepository.existsByServicioId(servicio.getId())).thenReturn(false);

        assertThrows(
                BadRequestException.class,
                () -> resenaService.registrar(
                        10L,
                        servicio.getId(),
                        new ResenaRequest(0, null, null, null, null, null)));
        assertThrows(
                BadRequestException.class,
                () -> resenaService.registrar(
                        10L,
                        servicio.getId(),
                        new ResenaRequest(5, 6, null, null, null, null)));
        assertThrows(
                BadRequestException.class,
                () -> resenaService.registrar(
                        10L,
                        servicio.getId(),
                        new ResenaRequest(5, null, null, null, null, "x".repeat(1001))));
    }

    @Test
    void responde_una_resena_solo_el_tecnico_del_servicio() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Resena resena = resena(servicio);
        when(servicioAccesoService.obtenerParaParticipante(20L, servicio.getId()))
                .thenReturn(servicio);
        when(resenaRepository.findByIdForUpdate(resena.getId()))
                .thenReturn(Optional.of(resena));

        ResenaResponse response = resenaService.responder(
                20L,
                servicio.getId(),
                resena.getId(),
                new RespuestaResenaRequest(" Gracias por su comentario "));

        assertEquals("Gracias por su comentario", response.respuestaTecnico());
        assertNotNull(response.fechaRespuesta());
        verify(resenaRepository).save(resena);
        verify(notificacionService).crear(
                eq(10L),
                eq(TipoNotificacion.RESENA_RESPUESTA),
                anyString(),
                anyString(),
                eq("RESENA"),
                eq(resena.getId()));
    }

    @Test
    void rechaza_una_segunda_respuesta_del_tecnico() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Resena resena = resena(servicio);
        resena.setRespuestaTecnico("Ya respondida");
        when(servicioAccesoService.obtenerParaParticipante(20L, servicio.getId()))
                .thenReturn(servicio);
        when(resenaRepository.findByIdForUpdate(resena.getId()))
                .thenReturn(Optional.of(resena));

        assertThrows(
                BusinessException.class,
                () -> resenaService.responder(
                        20L,
                        servicio.getId(),
                        resena.getId(),
                        new RespuestaResenaRequest("Otra respuesta")));

        verify(resenaRepository, never()).save(resena);
    }

    @Test
    void no_permite_responder_una_resena_de_otro_servicio() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Servicio otro = servicio(EstadoServicio.CALIFICADO);
        otro.setId(99L);
        Resena resena = resena(otro);
        when(servicioAccesoService.obtenerParaParticipante(20L, servicio.getId()))
                .thenReturn(servicio);
        when(resenaRepository.findByIdForUpdate(resena.getId()))
                .thenReturn(Optional.of(resena));

        assertThrows(
                NotFoundException.class,
                () -> resenaService.responder(
                        20L,
                        servicio.getId(),
                        resena.getId(),
                        new RespuestaResenaRequest("Respuesta")));
    }

    @Test
    void reporta_una_resena_visible_y_notifica_a_administracion_y_al_cliente() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Resena resena = resena(servicio);
        when(servicioAccesoService.obtenerParaParticipante(20L, servicio.getId()))
                .thenReturn(servicio);
        when(resenaRepository.findByIdForUpdate(resena.getId()))
                .thenReturn(Optional.of(resena));
        when(administradorRepository.buscarUsuarioIds()).thenReturn(List.of(1L, 2L));
        when(tecnicoRepository.findByIdForUpdate(servicio.getTecnico().getId()))
                .thenReturn(Optional.of(servicio.getTecnico()));
        when(resenaRepository.countByTecnicoIdAndEstado(
                servicio.getTecnico().getId(), EstadoResena.VISIBLE)).thenReturn(0L);

        ResenaResponse response = resenaService.reportar(
                20L,
                servicio.getId(),
                resena.getId(),
                new ReporteResenaRequest(" Lenguaje ofensivo "));

        assertEquals(EstadoResena.REPORTADA, response.estado());
        assertEquals("Lenguaje ofensivo", response.motivoReporte());
        assertEquals(0, servicio.getTecnico().getTotalResenas());
        assertEquals(new BigDecimal("0.00"),
                servicio.getTecnico().getCalificacionPromedio());
        verify(resenaRepository).save(resena);
        verify(notificacionService).crear(
                eq(1L), eq(TipoNotificacion.RESENA_REPORTADA),
                anyString(), contains("Lenguaje ofensivo"), eq("RESENA"), any());
        verify(notificacionService).crear(
                eq(2L), eq(TipoNotificacion.RESENA_REPORTADA),
                anyString(), anyString(), eq("RESENA"), any());
        verify(notificacionService).crear(
                eq(10L), eq(TipoNotificacion.RESENA_REPORTADA),
                anyString(), anyString(), eq("RESENA"), any());
    }

    @Test
    void rechaza_reportar_una_resena_que_no_esta_visible() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Resena resena = resena(servicio);
        resena.setEstado(EstadoResena.REPORTADA);
        when(servicioAccesoService.obtenerParaParticipante(20L, servicio.getId()))
                .thenReturn(servicio);
        when(resenaRepository.findByIdForUpdate(resena.getId()))
                .thenReturn(Optional.of(resena));

        assertThrows(
                BusinessException.class,
                () -> resenaService.reportar(
                        20L,
                        servicio.getId(),
                        resena.getId(),
                        new ReporteResenaRequest("Motivo")));
    }

    @Test
    void oculta_una_resena_reportada_y_recalcula_el_promedio() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Resena resena = resena(servicio);
        resena.setEstado(EstadoResena.REPORTADA);
        resena.setMotivoReporte("Ofensiva");
        Tecnico tecnico = servicio.getTecnico();
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(new Administrador()));
        when(resenaRepository.findByIdForUpdate(resena.getId()))
                .thenReturn(Optional.of(resena));
        when(tecnicoRepository.findByIdForUpdate(tecnico.getId()))
                .thenReturn(Optional.of(tecnico));
        when(resenaRepository.countByTecnicoIdAndEstado(
                tecnico.getId(), EstadoResena.VISIBLE)).thenReturn(0L);

        ResenaResponse response = resenaService.moderar(
                1L, resena.getId(), new ModeracionResenaRequest(EstadoResena.OCULTA));

        assertEquals(EstadoResena.OCULTA, response.estado());
        assertEquals("Ofensiva", response.motivoReporte());
        assertEquals(0, tecnico.getTotalResenas());
        assertEquals(new BigDecimal("0.00"), tecnico.getCalificacionPromedio());
        verify(auditoriaService).registrar(
                eq(1L), eq("MODERAR_RESENA"), eq("resena"), eq(resena.getId()),
                contains("REPORTADA"));
        verify(notificacionService).crear(
                eq(10L), eq(TipoNotificacion.RESENA_MODERADA),
                anyString(), anyString(), eq("RESENA"), any());
        verify(notificacionService).crear(
                eq(20L), eq(TipoNotificacion.RESENA_MODERADA),
                anyString(), anyString(), eq("RESENA"), any());
    }

    @Test
    void habilita_una_resena_reportada_y_limpia_el_motivo() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Resena resena = resena(servicio);
        resena.setEstado(EstadoResena.REPORTADA);
        resena.setMotivoReporte("Revisar");
        Tecnico tecnico = servicio.getTecnico();
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(new Administrador()));
        when(resenaRepository.findByIdForUpdate(resena.getId()))
                .thenReturn(Optional.of(resena));
        when(tecnicoRepository.findByIdForUpdate(tecnico.getId()))
                .thenReturn(Optional.of(tecnico));
        when(resenaRepository.countByTecnicoIdAndEstado(
                tecnico.getId(), EstadoResena.VISIBLE)).thenReturn(3L);
        when(resenaRepository.promedioEstrellas(
                tecnico.getId(), EstadoResena.VISIBLE)).thenReturn(4.0);

        ResenaResponse response = resenaService.moderar(
                1L, resena.getId(), new ModeracionResenaRequest(EstadoResena.VISIBLE));

        assertEquals(EstadoResena.VISIBLE, response.estado());
        assertNull(response.motivoReporte());
        assertEquals(3, tecnico.getTotalResenas());
        assertEquals(new BigDecimal("4.00"), tecnico.getCalificacionPromedio());
    }

    @Test
    void rechaza_moderar_una_resena_que_no_fue_reportada() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Resena resena = resena(servicio);
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(new Administrador()));
        when(resenaRepository.findByIdForUpdate(resena.getId()))
                .thenReturn(Optional.of(resena));

        assertThrows(
                BusinessException.class,
                () -> resenaService.moderar(
                        1L, resena.getId(),
                        new ModeracionResenaRequest(EstadoResena.OCULTA)));
    }

    @Test
    void rechaza_un_estado_de_moderacion_no_permitido() {
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(new Administrador()));

        assertThrows(
                BadRequestException.class,
                () -> resenaService.moderar(
                        1L, 7L,
                        new ModeracionResenaRequest(EstadoResena.REPORTADA)));
        verify(resenaRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void rechaza_la_moderacion_si_el_usuario_no_es_administrador() {
        when(administradorRepository.findByUsuarioId(1L)).thenReturn(Optional.empty());

        assertThrows(
                ForbiddenException.class,
                () -> resenaService.moderar(
                        1L, 7L,
                        new ModeracionResenaRequest(EstadoResena.OCULTA)));
    }

    @Test
    void obtiene_la_resena_del_servicio_y_falla_si_no_existe() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Resena resena = resena(servicio);
        when(servicioAccesoService.obtenerParaParticipante(10L, servicio.getId()))
                .thenReturn(servicio);
        when(resenaRepository.findByServicioId(servicio.getId()))
                .thenReturn(Optional.of(resena));

        assertEquals(5, resenaService.obtenerPorServicio(
                10L, servicio.getId()).estrellas());

        when(resenaRepository.findByServicioId(servicio.getId()))
                .thenReturn(Optional.empty());
        assertThrows(
                NotFoundException.class,
                () -> resenaService.obtenerPorServicio(10L, servicio.getId()));
    }

    @Test
    void lista_solo_las_resenas_visibles_de_un_tecnico() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Resena resena = resena(servicio);
        when(tecnicoRepository.findById(2L)).thenReturn(Optional.of(servicio.getTecnico()));
        when(resenaRepository.findByTecnicoIdAndEstadoOrderByCreadoEnDescIdDesc(
                2L, EstadoResena.VISIBLE)).thenReturn(List.of(resena));

        List<ResenaResponse> resenas = resenaService.listarPorTecnico(2L);

        assertEquals(1, resenas.size());
        assertEquals(5, resenas.get(0).estrellas());
    }

    @Test
    void falla_al_listar_resenas_de_un_tecnico_inexistente() {
        when(tecnicoRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> resenaService.listarPorTecnico(2L));
    }

    @Test
    void lista_resenas_para_administracion_con_y_sin_filtro() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Resena resena = resena(servicio);
        when(resenaRepository.findAllByOrderByCreadoEnDescIdDesc())
                .thenReturn(List.of(resena));
        when(resenaRepository.findByEstadoOrderByCreadoEnDescIdDesc(
                EstadoResena.REPORTADA)).thenReturn(List.of(resena));

        assertEquals(1, resenaService.listarAdmin(null).size());
        assertEquals(1, resenaService.listarAdmin(EstadoResena.REPORTADA).size());
    }

    private Servicio servicio(EstadoServicio estado) {
        Servicio servicio = ServicioFixture.servicio(
                5L, estado, Modalidad.DOMICILIO);
        servicio.setFechaConfirmacion(LocalDateTime.now().minusDays(1));
        return servicio;
    }

    private Resena resena(Servicio servicio) {
        return Resena.builder()
                .id(7L)
                .servicio(servicio)
                .cliente(servicio.getCliente())
                .tecnico(servicio.getTecnico())
                .estrellas(5)
                .puntualidad(5)
                .trato(4)
                .calidad(5)
                .precioJusto(4)
                .comentario("Muy buen servicio")
                .estado(EstadoResena.VISIBLE)
                .build();
    }

    private void prepararRegistro(Servicio servicio) {
        when(servicioAccesoService.bloquear(servicio.getId())).thenReturn(servicio);
        when(resenaRepository.existsByServicioId(servicio.getId())).thenReturn(false);
        when(parametroSistemaService.obtenerEntero(ParametroClave.DIAS_PARA_RESENAR))
                .thenReturn(15);
        when(tecnicoRepository.findByIdForUpdate(servicio.getTecnico().getId()))
                .thenReturn(Optional.of(servicio.getTecnico()));
    }
}
