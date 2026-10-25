package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.CambioEstadoReclamoRequest;
import com.fixtech.dto.ReclamoResponse;
import com.fixtech.dto.RespuestaReclamoRequest;
import com.fixtech.entity.Administrador;
import com.fixtech.entity.Reclamo;
import com.fixtech.entity.ReclamoFoto;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.EstadoReclamo;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.mapper.ReclamoMapper;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ReclamoFotoRepository;
import com.fixtech.repository.ReclamoRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ReclamoServiceTest {

    @Mock
    private ReclamoRepository reclamoRepository;

    @Mock
    private ReclamoFotoRepository reclamoFotoRepository;

    @Mock
    private AdministradorRepository administradorRepository;

    @Mock
    private ServicioAccesoService servicioAccesoService;

    @Mock
    private ArchivoService archivoService;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private AuditoriaService auditoriaService;

    @Spy
    private ReclamoMapper reclamoMapper = new ReclamoMapper();

    @InjectMocks
    private ReclamoService reclamoService;

    @Test
    void registra_un_reclamo_con_fotos_y_notifica_al_tecnico_y_administracion() {
        Servicio servicio = servicio(EstadoServicio.CONFIRMADO);
        MultipartFile foto = foto();
        when(servicioAccesoService.bloquear(servicio.getId())).thenReturn(servicio);
        when(reclamoRepository.existsByServicioIdAndEstadoIn(
                eq(servicio.getId()), any())).thenReturn(false);
        when(reclamoRepository.save(any(Reclamo.class))).thenAnswer(invocacion -> {
            Reclamo guardado = invocacion.getArgument(0);
            if (guardado.getId() == null) {
                guardado.setId(30L);
            }
            return guardado;
        });
        when(archivoService.guardarImagenReclamo(30L, foto))
                .thenReturn("reclamos/30/foto.png");
        when(administradorRepository.buscarUsuarioIds()).thenReturn(List.of(1L));

        ReclamoResponse response = reclamoService.registrar(
                10L,
                servicio.getId(),
                " El equipo presenta una falla ",
                List.of(foto));

        assertEquals(30L, response.id());
        assertEquals(servicio.getId(), response.servicioId());
        assertEquals("El equipo presenta una falla", response.descripcion());
        assertEquals(EstadoReclamo.ABIERTO, response.estado());
        assertEquals("Cliente Prueba", response.clienteNombre());
        assertEquals(1, response.fotoIds().size());
        verify(reclamoRepository, times(2)).save(any(Reclamo.class));
        verify(archivoService).guardarImagenReclamo(30L, foto);
        verify(notificacionService).crear(
                eq(20L), eq(TipoNotificacion.RECLAMO_REGISTRADO),
                anyString(), anyString(), eq("RECLAMO"), eq(30L));
        verify(notificacionService).crear(
                eq(1L), eq(TipoNotificacion.RECLAMO_REGISTRADO),
                anyString(), anyString(), eq("RECLAMO"), eq(30L));
    }

    @Test
    void rechaza_el_reclamo_si_el_usuario_no_es_el_cliente() {
        Servicio servicio = servicio(EstadoServicio.CONFIRMADO);
        when(servicioAccesoService.bloquear(servicio.getId())).thenReturn(servicio);
        doThrow(new ForbiddenException("Solo el cliente"))
                .when(servicioAccesoService).validarCliente(servicio, 99L);

        assertThrows(
                ForbiddenException.class,
                () -> reclamoService.registrar(
                        99L, servicio.getId(), "Falla", List.of(foto())));

        verify(reclamoRepository, never()).save(any());
    }

    @Test
    void rechaza_el_reclamo_si_el_servicio_no_esta_confirmado() {
        Servicio servicio = servicio(EstadoServicio.FINALIZADO);
        when(servicioAccesoService.bloquear(servicio.getId())).thenReturn(servicio);

        assertThrows(
                BusinessException.class,
                () -> reclamoService.registrar(
                        10L, servicio.getId(), "Falla", List.of(foto())));
    }

    @Test
    void rechaza_un_segundo_reclamo_activo_del_mismo_servicio() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        when(servicioAccesoService.bloquear(servicio.getId())).thenReturn(servicio);
        when(reclamoRepository.existsByServicioIdAndEstadoIn(
                eq(servicio.getId()), any())).thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> reclamoService.registrar(
                        10L, servicio.getId(), "Falla", List.of(foto())));
    }

    @Test
    void rechaza_una_descripcion_vacia_o_demasiado_larga() {
        Servicio servicio = servicio(EstadoServicio.CONFIRMADO);
        when(servicioAccesoService.bloquear(servicio.getId())).thenReturn(servicio);
        when(reclamoRepository.existsByServicioIdAndEstadoIn(
                eq(servicio.getId()), any())).thenReturn(false);

        assertThrows(
                BadRequestException.class,
                () -> reclamoService.registrar(
                        10L, servicio.getId(), "   ", List.of(foto())));
        assertThrows(
                BadRequestException.class,
                () -> reclamoService.registrar(
                        10L, servicio.getId(), "x".repeat(2001), List.of(foto())));
    }

    @Test
    void exige_entre_una_y_cinco_fotos_validas() {
        Servicio servicio = servicio(EstadoServicio.CONFIRMADO);
        when(servicioAccesoService.bloquear(servicio.getId())).thenReturn(servicio);
        when(reclamoRepository.existsByServicioIdAndEstadoIn(
                eq(servicio.getId()), any())).thenReturn(false);

        assertThrows(
                BadRequestException.class,
                () -> reclamoService.registrar(
                        10L, servicio.getId(), "Falla", List.of()));
        assertThrows(
                BadRequestException.class,
                () -> reclamoService.registrar(
                        10L, servicio.getId(), "Falla",
                        List.of(foto(), foto(), foto(), foto(), foto(), foto())));
        assertThrows(
                BadRequestException.class,
                () -> reclamoService.registrar(
                        10L, servicio.getId(), "Falla", Arrays.asList((MultipartFile) null)));
    }

    @Test
    void elimina_las_fotos_si_falla_el_registro() {
        Servicio servicio = servicio(EstadoServicio.CONFIRMADO);
        MultipartFile foto = foto();
        when(servicioAccesoService.bloquear(servicio.getId())).thenReturn(servicio);
        when(reclamoRepository.existsByServicioIdAndEstadoIn(
                eq(servicio.getId()), any())).thenReturn(false);
        when(reclamoRepository.save(any(Reclamo.class))).thenAnswer(invocacion -> {
            Reclamo guardado = invocacion.getArgument(0);
            if (guardado.getId() == null) {
                guardado.setId(30L);
            }
            return guardado;
        });
        when(archivoService.guardarImagenReclamo(30L, foto))
                .thenReturn("reclamos/30/foto.png");
        doThrow(new IllegalStateException("fallo al notificar"))
                .when(notificacionService).crear(
                        any(), any(), anyString(), anyString(), anyString(), any());

        assertThrows(
                IllegalStateException.class,
                () -> reclamoService.registrar(
                        10L, servicio.getId(), "Falla", List.of(foto)));

        verify(archivoService).eliminar("reclamos/30/foto.png");
    }

    @Test
    void lista_los_reclamos_del_servicio_para_un_participante() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Reclamo reclamo = reclamo(servicio);
        when(servicioAccesoService.obtenerParaParticipante(10L, servicio.getId()))
                .thenReturn(servicio);
        when(reclamoRepository.buscarPorServicio(servicio.getId()))
                .thenReturn(List.of(reclamo));

        List<ReclamoResponse> response = reclamoService.listarPorServicio(
                10L, servicio.getId());

        assertEquals(1, response.size());
        assertEquals(30L, response.get(0).id());
    }

    @Test
    void obtiene_el_detalle_solo_si_pertenece_al_servicio() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Reclamo reclamo = reclamo(servicio);
        when(servicioAccesoService.obtenerParaParticipante(10L, servicio.getId()))
                .thenReturn(servicio);
        when(reclamoRepository.buscarDetallePorId(reclamo.getId()))
                .thenReturn(Optional.of(reclamo));

        ReclamoResponse response = reclamoService.obtenerDetalle(
                10L, servicio.getId(), reclamo.getId());

        assertEquals(reclamo.getId(), response.id());

        Servicio otro = servicio(EstadoServicio.CALIFICADO);
        otro.setId(99L);
        Reclamo ajeno = reclamo(otro);
        when(reclamoRepository.buscarDetallePorId(ajeno.getId()))
                .thenReturn(Optional.of(ajeno));
        assertThrows(
                NotFoundException.class,
                () -> reclamoService.obtenerDetalle(
                        10L, servicio.getId(), ajeno.getId()));
    }

    @Test
    void falla_al_obtener_un_detalle_inexistente() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        when(servicioAccesoService.obtenerParaParticipante(10L, servicio.getId()))
                .thenReturn(servicio);
        when(reclamoRepository.buscarDetallePorId(30L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> reclamoService.obtenerDetalle(10L, servicio.getId(), 30L));
    }

    @Test
    void obtiene_una_foto_del_reclamo_solo_para_participantes() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        ReclamoFoto foto = ReclamoFoto.builder()
                .id(40L)
                .rutaArchivo("reclamos/30/foto.png")
                .build();
        ArchivoService.RecursoImagen imagen = new ArchivoService.RecursoImagen(
                new ByteArrayResource(new byte[]{1, 2, 3}), MediaType.IMAGE_PNG);
        when(servicioAccesoService.obtenerParaParticipante(10L, servicio.getId()))
                .thenReturn(servicio);
        when(reclamoFotoRepository.findByIdAndReclamoIdAndReclamoServicioId(
                40L, 30L, servicio.getId())).thenReturn(Optional.of(foto));
        when(archivoService.cargar("reclamos/30/foto.png")).thenReturn(imagen);

        ArchivoService.RecursoImagen resultado = reclamoService.obtenerFoto(
                10L, servicio.getId(), 30L, 40L);

        assertSame(imagen, resultado);
    }

    @Test
    void falla_al_obtener_una_foto_que_no_pertenece_al_reclamo() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        when(servicioAccesoService.obtenerParaParticipante(10L, servicio.getId()))
                .thenReturn(servicio);
        when(reclamoFotoRepository.findByIdAndReclamoIdAndReclamoServicioId(
                40L, 30L, servicio.getId())).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> reclamoService.obtenerFoto(
                        10L, servicio.getId(), 30L, 40L));
    }

    @Test
    void obtiene_una_foto_para_administracion() {
        ReclamoFoto foto = ReclamoFoto.builder()
                .id(40L)
                .rutaArchivo("reclamos/30/foto.png")
                .build();
        ArchivoService.RecursoImagen imagen = new ArchivoService.RecursoImagen(
                new ByteArrayResource(new byte[]{1, 2, 3}), MediaType.IMAGE_PNG);
        when(reclamoFotoRepository.findByIdAndReclamoId(40L, 30L))
                .thenReturn(Optional.of(foto));
        when(archivoService.cargar("reclamos/30/foto.png")).thenReturn(imagen);

        assertSame(imagen, reclamoService.obtenerFotoAdmin(30L, 40L));
    }

    @Test
    void falla_al_obtener_una_foto_administrativa_inexistente() {
        when(reclamoFotoRepository.findByIdAndReclamoId(40L, 30L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> reclamoService.obtenerFotoAdmin(30L, 40L));
    }

    @Test
    void el_tecnico_responde_un_reclamo_activo_y_notifica_al_cliente() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Reclamo reclamo = reclamo(servicio);
        when(servicioAccesoService.obtenerParaParticipante(20L, servicio.getId()))
                .thenReturn(servicio);
        when(reclamoRepository.findByIdForUpdate(reclamo.getId()))
                .thenReturn(Optional.of(reclamo));

        ReclamoResponse response = reclamoService.responder(
                20L,
                servicio.getId(),
                reclamo.getId(),
                new RespuestaReclamoRequest(" Revisaremos el caso "));

        assertEquals("Revisaremos el caso", response.respuestaTecnico());
        verify(reclamoRepository).save(reclamo);
        verify(notificacionService).crear(
                eq(10L), eq(TipoNotificacion.RECLAMO_RESPUESTA),
                anyString(), anyString(), eq("RECLAMO"), eq(reclamo.getId()));
    }

    @Test
    void rechaza_una_respuesta_duplicada_o_de_un_reclamo_finalizado() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Reclamo reclamo = reclamo(servicio);
        reclamo.setRespuestaTecnico("Ya respondido");
        when(servicioAccesoService.obtenerParaParticipante(20L, servicio.getId()))
                .thenReturn(servicio);
        when(reclamoRepository.findByIdForUpdate(reclamo.getId()))
                .thenReturn(Optional.of(reclamo));

        assertThrows(
                BusinessException.class,
                () -> reclamoService.responder(
                        20L, servicio.getId(), reclamo.getId(),
                        new RespuestaReclamoRequest("Otra")));

        Reclamo resuelto = reclamo(servicio);
        resuelto.setEstado(EstadoReclamo.RESUELTO);
        when(reclamoRepository.findByIdForUpdate(resuelto.getId()))
                .thenReturn(Optional.of(resuelto));
        assertThrows(
                BusinessException.class,
                () -> reclamoService.responder(
                        20L, servicio.getId(), resuelto.getId(),
                        new RespuestaReclamoRequest("Respuesta")));
    }

    @Test
    void no_permite_responder_un_reclamo_de_otro_servicio() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Servicio otro = servicio(EstadoServicio.CALIFICADO);
        otro.setId(99L);
        Reclamo ajeno = reclamo(otro);
        when(servicioAccesoService.obtenerParaParticipante(20L, servicio.getId()))
                .thenReturn(servicio);
        when(reclamoRepository.findByIdForUpdate(ajeno.getId()))
                .thenReturn(Optional.of(ajeno));

        assertThrows(
                NotFoundException.class,
                () -> reclamoService.responder(
                        20L, servicio.getId(), ajeno.getId(),
                        new RespuestaReclamoRequest("Respuesta")));
    }

    @Test
    void pasa_un_reclamo_a_revision_y_audita_el_cambio() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Reclamo reclamo = reclamo(servicio);
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(new Administrador()));
        when(reclamoRepository.findByIdForUpdate(reclamo.getId()))
                .thenReturn(Optional.of(reclamo));

        ReclamoResponse response = reclamoService.cambiarEstado(
                1L,
                reclamo.getId(),
                new CambioEstadoReclamoRequest(EstadoReclamo.EN_REVISION, null));

        assertEquals(EstadoReclamo.EN_REVISION, response.estado());
        assertNull(response.fechaResolucion());
        verify(auditoriaService).registrar(
                eq(1L), eq("REVISAR_RECLAMO"), eq("reclamo"), eq(reclamo.getId()),
                contains("EN_REVISION"));
        verify(notificacionService, times(2)).crear(
                any(), eq(TipoNotificacion.RECLAMO_EN_REVISION),
                anyString(), anyString(), eq("RECLAMO"), eq(reclamo.getId()));
    }

    @Test
    void exige_resolucion_para_marcar_un_reclamo_como_resuelto() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Reclamo reclamo = reclamo(servicio);
        reclamo.setEstado(EstadoReclamo.EN_REVISION);
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(new Administrador()));
        when(reclamoRepository.findByIdForUpdate(reclamo.getId()))
                .thenReturn(Optional.of(reclamo));

        assertThrows(
                BadRequestException.class,
                () -> reclamoService.cambiarEstado(
                        1L, reclamo.getId(),
                        new CambioEstadoReclamoRequest(EstadoReclamo.RESUELTO, "  ")));
    }

    @Test
    void resuelve_un_reclamo_con_resolucion_y_notifica_a_las_partes() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Reclamo reclamo = reclamo(servicio);
        reclamo.setEstado(EstadoReclamo.EN_REVISION);
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(new Administrador()));
        when(reclamoRepository.findByIdForUpdate(reclamo.getId()))
                .thenReturn(Optional.of(reclamo));

        ReclamoResponse response = reclamoService.cambiarEstado(
                1L,
                reclamo.getId(),
                new CambioEstadoReclamoRequest(
                        EstadoReclamo.RESUELTO, " Se coordinó la reparación "));

        assertEquals(EstadoReclamo.RESUELTO, response.estado());
        assertEquals("Se coordinó la reparación", response.resolucion());
        assertEquals(1L, response.resueltoPorId());
        assertNotNull(response.fechaResolucion());
        verify(auditoriaService).registrar(
                eq(1L), eq("RESOLVER_RECLAMO"), eq("reclamo"), eq(reclamo.getId()),
                contains("Se coordinó la reparación"));
        verify(notificacionService, times(2)).crear(
                any(), eq(TipoNotificacion.RECLAMO_RESUELTO),
                anyString(), contains("Se coordinó la reparación"),
                eq("RECLAMO"), eq(reclamo.getId()));
    }

    @Test
    void cierra_un_reclamo_resuelto_conservando_la_resolucion_y_la_fecha() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Reclamo reclamo = reclamo(servicio);
        reclamo.setEstado(EstadoReclamo.RESUELTO);
        reclamo.setResolucion("Resolución previa");
        LocalDateTime fecha = LocalDateTime.now().minusDays(1);
        reclamo.setFechaResolucion(fecha);
        reclamo.setResueltoPorId(9L);
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(new Administrador()));
        when(reclamoRepository.findByIdForUpdate(reclamo.getId()))
                .thenReturn(Optional.of(reclamo));

        ReclamoResponse response = reclamoService.cambiarEstado(
                1L, reclamo.getId(),
                new CambioEstadoReclamoRequest(EstadoReclamo.CERRADO, null));

        assertEquals(EstadoReclamo.CERRADO, response.estado());
        assertEquals("Resolución previa", response.resolucion());
        assertEquals(fecha, response.fechaResolucion());
        assertEquals(1L, response.resueltoPorId());
        verify(auditoriaService).registrar(
                eq(1L), eq("CERRAR_RECLAMO"), eq("reclamo"), eq(reclamo.getId()),
                contains("RESUELTO"));
    }

    @Test
    void exige_resolucion_para_cerrar_un_reclamo_en_revision() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Reclamo reclamo = reclamo(servicio);
        reclamo.setEstado(EstadoReclamo.EN_REVISION);
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(new Administrador()));
        when(reclamoRepository.findByIdForUpdate(reclamo.getId()))
                .thenReturn(Optional.of(reclamo));

        assertThrows(
                BadRequestException.class,
                () -> reclamoService.cambiarEstado(
                        1L, reclamo.getId(),
                        new CambioEstadoReclamoRequest(EstadoReclamo.CERRADO, null)));
    }

    @Test
    void rechaza_resolucion_al_pasar_a_revision() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Reclamo reclamo = reclamo(servicio);
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(new Administrador()));
        when(reclamoRepository.findByIdForUpdate(reclamo.getId()))
                .thenReturn(Optional.of(reclamo));

        assertThrows(
                BadRequestException.class,
                () -> reclamoService.cambiarEstado(
                        1L, reclamo.getId(),
                        new CambioEstadoReclamoRequest(
                                EstadoReclamo.EN_REVISION, "No corresponde")));
    }

    @Test
    void rechaza_transiciones_no_permitidas() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Reclamo abierto = reclamo(servicio);
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(new Administrador()));
        when(reclamoRepository.findByIdForUpdate(abierto.getId()))
                .thenReturn(Optional.of(abierto));

        assertThrows(
                BusinessException.class,
                () -> reclamoService.cambiarEstado(
                        1L, abierto.getId(),
                        new CambioEstadoReclamoRequest(EstadoReclamo.RESUELTO, "Resuelto")));
        assertThrows(
                BusinessException.class,
                () -> reclamoService.cambiarEstado(
                        1L, abierto.getId(),
                        new CambioEstadoReclamoRequest(EstadoReclamo.ABIERTO, null)));

        Reclamo cerrado = reclamo(servicio);
        cerrado.setEstado(EstadoReclamo.CERRADO);
        when(reclamoRepository.findByIdForUpdate(cerrado.getId()))
                .thenReturn(Optional.of(cerrado));
        assertThrows(
                BusinessException.class,
                () -> reclamoService.cambiarEstado(
                        1L, cerrado.getId(),
                        new CambioEstadoReclamoRequest(EstadoReclamo.CERRADO, "Resuelto")));
    }

    @Test
    void rechaza_la_transicion_si_el_usuario_no_es_administrador() {
        when(administradorRepository.findByUsuarioId(1L)).thenReturn(Optional.empty());

        assertThrows(
                ForbiddenException.class,
                () -> reclamoService.cambiarEstado(
                        1L, 30L,
                        new CambioEstadoReclamoRequest(EstadoReclamo.EN_REVISION, null)));
    }

    @Test
    void lista_reclamos_para_administracion_con_y_sin_filtro() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Reclamo reclamo = reclamo(servicio);
        when(reclamoRepository.listarTodos()).thenReturn(List.of(reclamo));
        when(reclamoRepository.listarPorEstado(
                EstadoReclamo.ABIERTO)).thenReturn(List.of(reclamo));

        assertEquals(1, reclamoService.listarAdmin(null).size());
        assertEquals(1, reclamoService.listarAdmin(EstadoReclamo.ABIERTO).size());
    }

    @Test
    void obtiene_el_detalle_administrativo_y_falla_si_no_existe() {
        Servicio servicio = servicio(EstadoServicio.CALIFICADO);
        Reclamo reclamo = reclamo(servicio);
        when(reclamoRepository.buscarDetallePorId(reclamo.getId()))
                .thenReturn(Optional.of(reclamo));

        assertEquals(reclamo.getId(), reclamoService.obtenerDetalleAdmin(
                reclamo.getId()).id());

        when(reclamoRepository.buscarDetallePorId(99L)).thenReturn(Optional.empty());
        assertThrows(
                NotFoundException.class,
                () -> reclamoService.obtenerDetalleAdmin(99L));
    }

    private Servicio servicio(EstadoServicio estado) {
        return ServicioFixture.servicio(5L, estado, Modalidad.DOMICILIO);
    }

    private Reclamo reclamo(Servicio servicio) {
        return Reclamo.builder()
                .id(30L)
                .servicio(servicio)
                .cliente(servicio.getCliente())
                .descripcion("Falla del equipo")
                .estado(EstadoReclamo.ABIERTO)
                .build();
    }

    private MultipartFile foto() {
        return new MockMultipartFile(
                "fotos", "foto.png", "image/png", new byte[]{1, 2, 3});
    }
}
