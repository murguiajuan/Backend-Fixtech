package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.PagoComisionResponse;
import com.fixtech.dto.ValidacionPagoComisionRequest;
import com.fixtech.entity.Administrador;
import com.fixtech.entity.Comision;
import com.fixtech.entity.PagoComision;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.enums.EstadoComision;
import com.fixtech.entity.enums.EstadoPagoComision;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.MetodoPago;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.Rol;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.mapper.ComisionMapper;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ComisionRepository;
import com.fixtech.repository.PagoComisionDetalleRepository;
import com.fixtech.repository.PagoComisionRepository;
import com.fixtech.repository.TecnicoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class PagoComisionServiceTest {

    @Mock
    private PagoComisionRepository pagoComisionRepository;

    @Mock
    private PagoComisionDetalleRepository pagoComisionDetalleRepository;

    @Mock
    private ComisionRepository comisionRepository;

    @Mock
    private TecnicoRepository tecnicoRepository;

    @Mock
    private AdministradorRepository administradorRepository;

    @Mock
    private ArchivoService archivoService;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private DeudaTecnicoService deudaTecnicoService;

    @Mock
    private AuditoriaService auditoriaService;

    @Spy
    private ComisionMapper comisionMapper = new ComisionMapper();

    @InjectMocks
    private PagoComisionService pagoComisionService;

    @Test
    void registra_un_pago_por_comisiones_completas_y_notifica_a_los_administradores() {
        Tecnico tecnico = tecnico();
        Servicio servicio = servicio();
        Comision primera = ServicioFixture.comision(
                1L, servicio, "6.60", LocalDate.now().plusDays(3));
        Comision segunda = ServicioFixture.comision(
                2L, servicio, "3.40", LocalDate.now().plusDays(5));
        MultipartFile comprobante = comprobante();

        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(tecnicoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tecnico));
        when(pagoComisionRepository.existsByTecnicoIdAndNumeroOperacionAndEstadoIn(
                eq(2L), eq("OP-001"), anyCollection())).thenReturn(false);
        when(comisionRepository.findByIdsForUpdate(List.of(2L, 1L)))
                .thenReturn(List.of(segunda, primera));
        when(pagoComisionDetalleRepository.buscarComisionesConPagoActivo(
                List.of(2L, 1L), java.util.EnumSet.of(
                        EstadoPagoComision.PENDIENTE,
                        EstadoPagoComision.VALIDADO)))
                .thenReturn(List.of());
        when(archivoService.guardarComprobantePago(2L, comprobante))
                .thenReturn("pagos/2/comprobante.png");
        when(administradorRepository.buscarUsuarioIds()).thenReturn(List.of(1L));
        when(pagoComisionRepository.save(any(PagoComision.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        PagoComisionResponse response = pagoComisionService.registrar(
                20L, List.of(2L, 1L), MetodoPago.YAPE, " OP-001 ",
                comprobante);

        assertEquals(new BigDecimal("10.00"), response.monto());
        assertEquals(MetodoPago.YAPE, response.metodo());
        assertEquals("OP-001", response.numeroOperacion());
        assertEquals(EstadoPagoComision.PENDIENTE, response.estado());
        assertTrue(response.tieneComprobante());
        assertEquals(2, response.comisiones().size());
        assertEquals(1L, response.comisiones().get(0).id());
        assertEquals(2L, response.comisiones().get(1).id());

        ArgumentCaptor<PagoComision> captor = ArgumentCaptor.forClass(PagoComision.class);
        verify(pagoComisionRepository).save(captor.capture());
        PagoComision guardado = captor.getValue();
        assertEquals(2, guardado.getDetalles().size());
        assertEquals(1L, guardado.getDetalles().get(0).getComision().getId());
        assertEquals(2L, guardado.getDetalles().get(1).getComision().getId());
        assertSame(guardado, guardado.getDetalles().get(0).getPago());

        verify(notificacionService, times(2)).crear(
                any(), eq(TipoNotificacion.PAGO_COMISION_REGISTRADO),
                anyString(), anyString(), eq("PAGO_COMISION"), any());
        verify(notificacionService).crear(
                eq(20L),
                eq(TipoNotificacion.PAGO_COMISION_REGISTRADO),
                anyString(),
                contains("S/ 10.00"),
                eq("PAGO_COMISION"),
                any());
        verify(notificacionService).crear(
                eq(1L),
                eq(TipoNotificacion.PAGO_COMISION_REGISTRADO),
                anyString(),
                contains("Tecnico Prueba"),
                eq("PAGO_COMISION"),
                any());
        verify(auditoriaService, never()).registrar(
                any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void rechaza_un_metodo_de_pago_no_permitido() {
        Tecnico tecnico = tecnico();
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(tecnicoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tecnico));

        BadRequestException excepcion = assertThrows(
                BadRequestException.class,
                () -> pagoComisionService.registrar(
                        20L, List.of(1L), MetodoPago.EFECTIVO, "OP-001",
                        comprobante()));

        assertTrue(excepcion.getMessage().contains("YAPE"));
        verify(archivoService, never()).guardarComprobantePago(any(), any());
    }

    @Test
    void rechaza_una_lista_de_comisiones_vacia_repetida_o_con_nulos() {
        Tecnico tecnico = tecnico();
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(tecnicoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tecnico));

        assertThrows(BadRequestException.class,
                () -> pagoComisionService.registrar(
                        20L, List.of(), MetodoPago.YAPE, "OP-001", comprobante()));
        assertThrows(BadRequestException.class,
                () -> pagoComisionService.registrar(
                        20L, List.of(1L, 1L), MetodoPago.YAPE, "OP-001", comprobante()));
        assertThrows(BadRequestException.class,
                () -> pagoComisionService.registrar(
                        20L, java.util.Arrays.asList(1L, null), MetodoPago.YAPE,
                        "OP-001", comprobante()));
    }

    @Test
    void rechaza_un_numero_de_operacion_vacio_o_demasiado_largo() {
        Tecnico tecnico = tecnico();
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(tecnicoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tecnico));

        assertThrows(BadRequestException.class,
                () -> pagoComisionService.registrar(
                        20L, List.of(1L), MetodoPago.YAPE, "   ", comprobante()));
        assertThrows(BadRequestException.class,
                () -> pagoComisionService.registrar(
                        20L, List.of(1L), MetodoPago.YAPE, "X".repeat(41),
                        comprobante()));
    }

    @Test
    void rechaza_un_numero_de_operacion_ya_registrado_en_un_pago_activo() {
        Tecnico tecnico = tecnico();
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(tecnicoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tecnico));
        when(pagoComisionRepository.existsByTecnicoIdAndNumeroOperacionAndEstadoIn(
                eq(2L), eq("OP-001"), anyCollection())).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> pagoComisionService.registrar(
                        20L, List.of(1L), MetodoPago.YAPE, "OP-001", comprobante()));
        verify(archivoService, never()).guardarComprobantePago(any(), any());
    }

    @Test
    void rechaza_una_comision_inexistente() {
        Tecnico tecnico = tecnico();
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(tecnicoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tecnico));
        when(pagoComisionRepository.existsByTecnicoIdAndNumeroOperacionAndEstadoIn(
                eq(2L), eq("OP-001"), anyCollection())).thenReturn(false);
        when(comisionRepository.findByIdsForUpdate(List.of(1L))).thenReturn(List.of());

        assertThrows(NotFoundException.class,
                () -> pagoComisionService.registrar(
                        20L, List.of(1L), MetodoPago.YAPE, "OP-001", comprobante()));
        verify(archivoService, never()).guardarComprobantePago(any(), any());
    }

    @Test
    void rechaza_una_comision_de_otro_tecnico() {
        Tecnico tecnico = tecnico();
        Comision ajena = ServicioFixture.comision(
                1L, servicio(), "6.60", LocalDate.now().plusDays(3));
        ajena.setTecnico(ServicioFixture.tecnico(99L, 990L));
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(tecnicoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tecnico));
        when(pagoComisionRepository.existsByTecnicoIdAndNumeroOperacionAndEstadoIn(
                eq(2L), eq("OP-001"), anyCollection())).thenReturn(false);
        when(comisionRepository.findByIdsForUpdate(List.of(1L)))
                .thenReturn(List.of(ajena));

        assertThrows(ForbiddenException.class,
                () -> pagoComisionService.registrar(
                        20L, List.of(1L), MetodoPago.PLIN, "OP-001", comprobante()));
    }

    @Test
    void rechaza_una_comision_que_ya_no_esta_pendiente() {
        Tecnico tecnico = tecnico();
        Comision pagada = ServicioFixture.comision(
                1L, servicio(), "6.60", LocalDate.now().plusDays(3));
        pagada.setEstado(EstadoComision.PAGADA);
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(tecnicoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tecnico));
        when(pagoComisionRepository.existsByTecnicoIdAndNumeroOperacionAndEstadoIn(
                eq(2L), eq("OP-001"), anyCollection())).thenReturn(false);
        when(comisionRepository.findByIdsForUpdate(List.of(1L)))
                .thenReturn(List.of(pagada));

        assertThrows(BusinessException.class,
                () -> pagoComisionService.registrar(
                        20L, List.of(1L), MetodoPago.TRANSFERENCIA, "OP-001",
                        comprobante()));
    }

    @Test
    void rechaza_una_comision_incluida_en_un_pago_activo() {
        Tecnico tecnico = tecnico();
        Comision comision = ServicioFixture.comision(
                1L, servicio(), "6.60", LocalDate.now().plusDays(3));
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(tecnicoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tecnico));
        when(pagoComisionRepository.existsByTecnicoIdAndNumeroOperacionAndEstadoIn(
                eq(2L), eq("OP-001"), anyCollection())).thenReturn(false);
        when(comisionRepository.findByIdsForUpdate(List.of(1L)))
                .thenReturn(List.of(comision));
        when(pagoComisionDetalleRepository.buscarComisionesConPagoActivo(
                eq(List.of(1L)), anyCollection())).thenReturn(List.of(1L));

        assertThrows(BusinessException.class,
                () -> pagoComisionService.registrar(
                        20L, List.of(1L), MetodoPago.YAPE, "OP-001", comprobante()));
    }

    @Test
    void elimina_el_comprobante_si_falla_el_guardado_del_pago() {
        Tecnico tecnico = tecnico();
        Comision comision = ServicioFixture.comision(
                1L, servicio(), "6.60", LocalDate.now().plusDays(3));
        MultipartFile comprobante = comprobante();
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(tecnicoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tecnico));
        when(pagoComisionRepository.existsByTecnicoIdAndNumeroOperacionAndEstadoIn(
                eq(2L), eq("OP-001"), anyCollection())).thenReturn(false);
        when(comisionRepository.findByIdsForUpdate(List.of(1L)))
                .thenReturn(List.of(comision));
        when(pagoComisionDetalleRepository.buscarComisionesConPagoActivo(
                eq(List.of(1L)), anyCollection())).thenReturn(List.of());
        when(archivoService.guardarComprobantePago(2L, comprobante))
                .thenReturn("pagos/2/comprobante.png");
        when(pagoComisionRepository.save(any(PagoComision.class)))
                .thenThrow(new IllegalStateException("fallo de base de datos"));

        assertThrows(IllegalStateException.class,
                () -> pagoComisionService.registrar(
                        20L, List.of(1L), MetodoPago.YAPE, "OP-001", comprobante));

        verify(archivoService).eliminar("pagos/2/comprobante.png");
    }

    @Test
    void lista_los_pagos_del_tecnico_con_filtro_y_sin_filtro() {
        Tecnico tecnico = tecnico();
        PagoComision pago = pago(tecnico, EstadoPagoComision.PENDIENTE, comision());
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(pagoComisionRepository.findByTecnicoIdOrderByCreadoEnDescIdDesc(2L))
                .thenReturn(List.of(pago));
        when(pagoComisionRepository
                .findByTecnicoIdAndEstadoOrderByCreadoEnDescIdDesc(
                        2L, EstadoPagoComision.PENDIENTE))
                .thenReturn(List.of(pago));

        List<PagoComisionResponse> todos = pagoComisionService.listarMios(20L, null);
        List<PagoComisionResponse> pendientes = pagoComisionService.listarMios(
                20L, EstadoPagoComision.PENDIENTE);

        assertEquals(1, todos.size());
        assertEquals(1, pendientes.size());
        assertEquals(EstadoPagoComision.PENDIENTE, pendientes.get(0).estado());
    }

    @Test
    void falla_al_listar_pagos_si_el_usuario_no_es_tecnico() {
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> pagoComisionService.listarMios(20L, null));
    }

    @Test
    void obtiene_el_comprobante_del_tecnico_solo_si_le_pertenece() {
        Tecnico tecnico = tecnico();
        PagoComision pago = pago(tecnico, EstadoPagoComision.PENDIENTE, comision());
        Resource resource = new ByteArrayResource(new byte[]{1, 2, 3});
        ArchivoService.RecursoImagen imagen = new ArchivoService.RecursoImagen(
                resource, MediaType.IMAGE_PNG);
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(pagoComisionRepository.findByIdAndTecnicoId(7L, 2L))
                .thenReturn(Optional.of(pago));
        when(archivoService.cargar("pagos/2/comprobante.png")).thenReturn(imagen);

        ArchivoService.RecursoImagen resultado = pagoComisionService
                .obtenerComprobanteTecnico(20L, 7L);

        assertSame(imagen, resultado);
        assertEquals(MediaType.IMAGE_PNG, resultado.tipoContenido());
    }

    @Test
    void falla_al_obtener_un_comprobante_ajeno_o_inexistente() {
        Tecnico tecnico = tecnico();
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(pagoComisionRepository.findByIdAndTecnicoId(7L, 2L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> pagoComisionService.obtenerComprobanteTecnico(20L, 7L));
    }

    @Test
    void lista_pagos_para_el_administrador_con_filtro_y_sin_filtro() {
        Tecnico tecnico = tecnico();
        PagoComision pago = pago(tecnico, EstadoPagoComision.PENDIENTE, comision());
        when(pagoComisionRepository.findAllByOrderByCreadoEnDescIdDesc())
                .thenReturn(List.of(pago));
        when(pagoComisionRepository.findByEstadoOrderByCreadoEnAscIdAsc(
                EstadoPagoComision.RECHAZADO)).thenReturn(List.of(pago));

        assertEquals(1, pagoComisionService.listarAdmin(null).size());
        assertEquals(1, pagoComisionService.listarAdmin(
                EstadoPagoComision.RECHAZADO).size());
    }

    @Test
    void obtiene_el_detalle_de_un_pago_para_el_administrador() {
        Tecnico tecnico = tecnico();
        PagoComision pago = pago(tecnico, EstadoPagoComision.PENDIENTE, comision());
        when(pagoComisionRepository.buscarDetallePorId(7L))
                .thenReturn(Optional.of(pago));

        PagoComisionResponse response = pagoComisionService.obtenerDetalleAdmin(7L);

        assertEquals(7L, response.id());
        assertEquals(1, response.comisiones().size());
    }

    @Test
    void falla_al_obtener_el_detalle_de_un_pago_inexistente() {
        when(pagoComisionRepository.buscarDetallePorId(7L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> pagoComisionService.obtenerDetalleAdmin(7L));
    }

    @Test
    void obtiene_el_comprobante_para_el_administrador() {
        Tecnico tecnico = tecnico();
        PagoComision pago = pago(tecnico, EstadoPagoComision.PENDIENTE, comision());
        ArchivoService.RecursoImagen imagen = new ArchivoService.RecursoImagen(
                new ByteArrayResource(new byte[]{1}), MediaType.IMAGE_JPEG);
        when(pagoComisionRepository.findById(7L)).thenReturn(Optional.of(pago));
        when(archivoService.cargar("pagos/2/comprobante.png")).thenReturn(imagen);

        assertSame(imagen, pagoComisionService.obtenerComprobanteAdmin(7L));
    }

    @Test
    void falla_al_obtener_el_comprobante_de_un_pago_inexistente() {
        when(pagoComisionRepository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> pagoComisionService.obtenerComprobanteAdmin(7L));
    }

    @Test
    void valida_un_pago_y_marca_las_comisiones_como_pagadas() {
        Tecnico tecnico = tecnico();
        Comision comision = comision();
        PagoComision pago = pago(tecnico, EstadoPagoComision.PENDIENTE, comision);
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(administrador()));
        when(pagoComisionRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(pago));

        PagoComisionResponse response = pagoComisionService.validar(
                1L, 7L, new ValidacionPagoComisionRequest(
                        EstadoPagoComision.VALIDADO, "  "));

        assertEquals(EstadoPagoComision.VALIDADO, response.estado());
        assertNull(response.observacion());
        assertNotNull(response.fechaValidacion());
        assertEquals(EstadoComision.PAGADA, comision.getEstado());
        assertNotNull(comision.getFechaPago());
        verify(comisionRepository).save(comision);
        verify(pagoComisionRepository).save(pago);
        verify(deudaTecnicoService).sincronizar(tecnico);
        verify(notificacionService).crear(
                eq(20L),
                eq(TipoNotificacion.PAGO_COMISION_VALIDADO),
                anyString(),
                contains("S/ 6.60"),
                eq("PAGO_COMISION"),
                eq(7L));
        verify(auditoriaService).registrar(
                eq(1L),
                eq("VALIDAR_PAGO_COMISION"),
                eq("pago_comision"),
                eq(7L),
                contains("Pago validado por S/ 6.60"));
    }

    @Test
    void rechaza_un_pago_con_observacion_sin_modificar_las_comisiones() {
        Tecnico tecnico = tecnico();
        Comision comision = comision();
        PagoComision pago = pago(tecnico, EstadoPagoComision.PENDIENTE, comision);
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(administrador()));
        when(pagoComisionRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(pago));

        PagoComisionResponse response = pagoComisionService.validar(
                1L, 7L, new ValidacionPagoComisionRequest(
                        EstadoPagoComision.RECHAZADO, "  Comprobante ilegible  "));

        assertEquals(EstadoPagoComision.RECHAZADO, response.estado());
        assertEquals("Comprobante ilegible", response.observacion());
        assertEquals(EstadoComision.PENDIENTE, comision.getEstado());
        assertNull(comision.getFechaPago());
        verify(comisionRepository, never()).save(any(Comision.class));
        verify(deudaTecnicoService, never()).sincronizar(any());
        verify(notificacionService).crear(
                eq(20L),
                eq(TipoNotificacion.PAGO_COMISION_RECHAZADO),
                anyString(),
                contains("Comprobante ilegible"),
                eq("PAGO_COMISION"),
                eq(7L));
        verify(auditoriaService).registrar(
                eq(1L),
                eq("RECHAZAR_PAGO_COMISION"),
                eq("pago_comision"),
                eq(7L),
                contains("Comprobante ilegible"));
    }

    @Test
    void rechaza_la_validacion_si_el_usuario_no_es_administrador() {
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> pagoComisionService.validar(
                        1L, 7L, new ValidacionPagoComisionRequest(
                                EstadoPagoComision.VALIDADO, null)));
    }

    @Test
    void rechaza_la_validacion_si_el_pago_no_existe() {
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(administrador()));
        when(pagoComisionRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> pagoComisionService.validar(
                        1L, 7L, new ValidacionPagoComisionRequest(
                                EstadoPagoComision.VALIDADO, null)));
    }

    @Test
    void rechaza_la_validacion_de_un_pago_que_ya_no_esta_pendiente() {
        Tecnico tecnico = tecnico();
        PagoComision pago = pago(tecnico, EstadoPagoComision.VALIDADO, comision());
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(administrador()));
        when(pagoComisionRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(pago));

        assertThrows(BusinessException.class,
                () -> pagoComisionService.validar(
                        1L, 7L, new ValidacionPagoComisionRequest(
                                EstadoPagoComision.RECHAZADO, "motivo")));
    }

    @Test
    void rechaza_un_estado_de_validacion_distinto_de_validado_o_rechazado() {
        Tecnico tecnico = tecnico();
        PagoComision pago = pago(tecnico, EstadoPagoComision.PENDIENTE, comision());
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(administrador()));
        when(pagoComisionRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(pago));

        assertThrows(BadRequestException.class,
                () -> pagoComisionService.validar(
                        1L, 7L, new ValidacionPagoComisionRequest(
                                EstadoPagoComision.PENDIENTE, null)));
    }

    @Test
    void exige_observacion_al_rechazar_un_pago() {
        Tecnico tecnico = tecnico();
        PagoComision pago = pago(tecnico, EstadoPagoComision.PENDIENTE, comision());
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(administrador()));
        when(pagoComisionRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(pago));

        assertThrows(BadRequestException.class,
                () -> pagoComisionService.validar(
                        1L, 7L, new ValidacionPagoComisionRequest(
                                EstadoPagoComision.RECHAZADO, "   ")));
    }

    @Test
    void rechaza_una_observacion_demasiado_larga() {
        Tecnico tecnico = tecnico();
        PagoComision pago = pago(tecnico, EstadoPagoComision.PENDIENTE, comision());
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(administrador()));
        when(pagoComisionRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(pago));

        assertThrows(BadRequestException.class,
                () -> pagoComisionService.validar(
                        1L, 7L, new ValidacionPagoComisionRequest(
                                EstadoPagoComision.VALIDADO, "X".repeat(301))));
    }

    @Test
    void rechaza_la_validacion_si_una_comision_ya_no_esta_pendiente() {
        Tecnico tecnico = tecnico();
        Comision comision = comision();
        comision.setEstado(EstadoComision.PAGADA);
        PagoComision pago = pago(tecnico, EstadoPagoComision.PENDIENTE, comision);
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(administrador()));
        when(pagoComisionRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(pago));

        assertThrows(BusinessException.class,
                () -> pagoComisionService.validar(
                        1L, 7L, new ValidacionPagoComisionRequest(
                                EstadoPagoComision.VALIDADO, null)));
    }

    @Test
    void rechaza_la_validacion_si_el_pago_no_tiene_comisiones() {
        Tecnico tecnico = tecnico();
        PagoComision pago = pago(tecnico, EstadoPagoComision.PENDIENTE);
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(administrador()));
        when(pagoComisionRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(pago));

        assertThrows(BusinessException.class,
                () -> pagoComisionService.validar(
                        1L, 7L, new ValidacionPagoComisionRequest(
                                EstadoPagoComision.VALIDADO, null)));
    }

    private Tecnico tecnico() {
        Tecnico tecnico = ServicioFixture.tecnico(2L, 20L);
        tecnico.setNombres("Tecnico");
        tecnico.setApellidos("Prueba");
        return tecnico;
    }

    private Servicio servicio() {
        return ServicioFixture.servicio(
                5L, EstadoServicio.CONFIRMADO, Modalidad.DOMICILIO);
    }

    private Comision comision() {
        return ServicioFixture.comision(
                1L, servicio(), "6.60", LocalDate.now().plusDays(3));
    }

    private PagoComision pago(
            Tecnico tecnico, EstadoPagoComision estado, Comision... comisiones) {
        PagoComision pago = PagoComision.builder()
                .id(7L)
                .tecnico(tecnico)
                .monto(new BigDecimal("6.60"))
                .metodo(MetodoPago.YAPE)
                .numeroOperacion("OP-001")
                .rutaComprobante("pagos/2/comprobante.png")
                .estado(estado)
                .build();
        pago.setCreadoEn(LocalDateTime.now());
        for (Comision comision : comisiones) {
            pago.agregarComision(comision);
        }
        return pago;
    }

    private Administrador administrador() {
        return Administrador.builder()
                .id(1L)
                .usuario(ServicioFixture.usuario(1L, Rol.ADMIN))
                .nombres("Admin")
                .apellidos("Prueba")
                .build();
    }

    private MultipartFile comprobante() {
        return new MockMultipartFile(
                "comprobante", "pago.png", "image/png", new byte[]{1, 2, 3});
    }
}
