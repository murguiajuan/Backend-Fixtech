package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.ContraofertaRequest;
import com.fixtech.dto.RespuestaNegociacionResponse;
import com.fixtech.dto.ServicioResponse;
import com.fixtech.entity.Cliente;
import com.fixtech.entity.Contraoferta;
import com.fixtech.entity.Distrito;
import com.fixtech.entity.Oferta;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.Solicitud;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.AccionContraoferta;
import com.fixtech.entity.enums.EmisorContraoferta;
import com.fixtech.entity.enums.EstadoContraoferta;
import com.fixtech.entity.enums.EstadoOferta;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.EstadoSolicitud;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.entity.enums.Rol;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.mapper.OfertaMapper;
import com.fixtech.repository.OfertaRepository;
import com.fixtech.repository.SolicitudRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NegociacionServiceTest {

    private static final LocalDateTime FUTURO = LocalDateTime.now().plusDays(1);

    @Mock
    private OfertaRepository ofertaRepository;

    @Mock
    private SolicitudRepository solicitudRepository;

    @Mock
    private ParametroSistemaService parametroSistemaService;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private ServicioService servicioService;

    @Spy
    private OfertaMapper ofertaMapper = new OfertaMapper();

    @InjectMocks
    private NegociacionService negociacionService;

    @Test
    void cliente_inicia_la_contraoferta_y_se_notifica_al_tecnico() {
        Contexto contexto = contexto(EstadoOferta.ENVIADA);
        prepararBloqueo(contexto);
        when(parametroSistemaService.obtenerEntero(
                ParametroClave.MAX_RONDAS_CONTRAOFERTA)).thenReturn(3);
        when(ofertaRepository.save(any(Oferta.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RespuestaNegociacionResponse response = negociacionService.responder(
                contexto.clienteUsuarioId(),
                contexto.oferta().getId(),
                new ContraofertaRequest(
                        AccionContraoferta.CONTRAOFERTA,
                        new BigDecimal("80.00"),
                        "Puedo pagar 80"));

        assertEquals(EstadoOferta.CONTRAOFERTADA, response.oferta().estado());
        assertEquals(1, response.oferta().contraofertas().size());
        assertEquals(EmisorContraoferta.CLIENTE,
                response.oferta().contraofertas().get(0).emisor());
        assertEquals(EstadoContraoferta.PENDIENTE,
                response.oferta().contraofertas().get(0).estado());
        assertEquals(new BigDecimal("100.00"), contexto.oferta().getPrecioServicio());
        verify(notificacionService).crear(
                eq(contexto.tecnicoUsuarioId()),
                eq(TipoNotificacion.CONTRAOFERTA_RECIBIDA),
                anyString(),
                anyString(),
                eq("OFERTA"),
                eq(contexto.oferta().getId()));
    }

    @Test
    void el_tecnico_no_puede_iniciar_una_contraoferta() {
        Contexto contexto = contexto(EstadoOferta.ENVIADA);
        prepararBloqueo(contexto);

        assertThrows(
                BusinessException.class,
                () -> negociacionService.responder(
                        contexto.tecnicoUsuarioId(),
                        contexto.oferta().getId(),
                        new ContraofertaRequest(
                                AccionContraoferta.CONTRAOFERTA,
                                new BigDecimal("80.00"),
                                null)));
    }

    @Test
    void rechaza_contraoferta_al_alcanzar_el_maximo_de_rondas() {
        Contexto contexto = contexto(EstadoOferta.CONTRAOFERTADA);
        contexto.oferta().getContraofertas().add(contraoferta(
                contexto.oferta(), EmisorContraoferta.CLIENTE, 1,
                "90.00", EstadoContraoferta.SUPERADA));
        contexto.oferta().getContraofertas().add(contraoferta(
                contexto.oferta(), EmisorContraoferta.TECNICO, 2,
                "85.00", EstadoContraoferta.SUPERADA));
        contexto.oferta().getContraofertas().add(contraoferta(
                contexto.oferta(), EmisorContraoferta.CLIENTE, 3,
                "82.00", EstadoContraoferta.PENDIENTE));
        prepararBloqueo(contexto);
        when(parametroSistemaService.obtenerEntero(
                ParametroClave.MAX_RONDAS_CONTRAOFERTA)).thenReturn(3);

        assertThrows(
                BusinessException.class,
                () -> negociacionService.responder(
                        contexto.tecnicoUsuarioId(),
                        contexto.oferta().getId(),
                        new ContraofertaRequest(
                                AccionContraoferta.CONTRAOFERTA,
                                new BigDecimal("80.00"),
                                null)));
    }

    @Test
    void no_permite_que_el_mismo_emisor_envie_dos_contraofertas_seguidas() {
        Contexto contexto = contexto(EstadoOferta.CONTRAOFERTADA);
        contexto.oferta().getContraofertas().add(contraoferta(
                contexto.oferta(), EmisorContraoferta.CLIENTE, 1,
                "80.00", EstadoContraoferta.PENDIENTE));
        prepararBloqueo(contexto);

        assertThrows(
                BusinessException.class,
                () -> negociacionService.responder(
                        contexto.clienteUsuarioId(),
                        contexto.oferta().getId(),
                        new ContraofertaRequest(
                                AccionContraoferta.CONTRAOFERTA,
                                new BigDecimal("75.00"),
                                null)));
    }

    @Test
    void el_tecnico_acepta_la_contraoferta_del_cliente_y_actualiza_el_precio() {
        Contexto contexto = contexto(EstadoOferta.CONTRAOFERTADA);
        Contraoferta pendiente = contraoferta(
                contexto.oferta(), EmisorContraoferta.CLIENTE, 1,
                "80.00", EstadoContraoferta.PENDIENTE);
        contexto.oferta().getContraofertas().add(pendiente);
        prepararBloqueo(contexto);
        when(ofertaRepository.save(any(Oferta.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RespuestaNegociacionResponse response = negociacionService.responder(
                contexto.tecnicoUsuarioId(),
                contexto.oferta().getId(),
                new ContraofertaRequest(AccionContraoferta.ACEPTAR, null, null));

        assertEquals(new BigDecimal("80.00"), contexto.oferta().getPrecioServicio());
        assertEquals(EstadoOferta.ENVIADA, contexto.oferta().getEstado());
        assertEquals(EstadoContraoferta.ACEPTADA, pendiente.getEstado());
        assertNull(response.servicio());
        verify(servicioService, never()).crearDesdeOferta(any(), any());
        verify(notificacionService).crear(
                eq(contexto.clienteUsuarioId()),
                eq(TipoNotificacion.OFERTA_ACTUALIZADA),
                anyString(),
                anyString(),
                eq("OFERTA"),
                eq(contexto.oferta().getId()));
    }

    @Test
    void el_cliente_acepta_la_contraoferta_del_tecnico_y_se_crea_el_servicio() {
        Contexto contexto = contexto(EstadoOferta.CONTRAOFERTADA);
        Contraoferta pendiente = contraoferta(
                contexto.oferta(), EmisorContraoferta.TECNICO, 1,
                "90.00", EstadoContraoferta.PENDIENTE);
        contexto.oferta().getContraofertas().add(pendiente);
        Oferta otra = oferta(200L, contexto.solicitud(),
                tecnico(3L, 30L, contexto.distrito()), EstadoOferta.ENVIADA);
        Servicio servicio = servicio(contexto, 300L);

        prepararBloqueo(contexto);
        when(ofertaRepository.save(any(Oferta.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(solicitudRepository.save(any(Solicitud.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(ofertaRepository.buscarOtrasActivas(
                eq(contexto.solicitud().getId()),
                eq(contexto.oferta().getId()),
                any())).thenReturn(List.of(otra));
        when(ofertaRepository.rechazarDemas(
                eq(contexto.solicitud().getId()),
                eq(contexto.oferta().getId()),
                any(),
                eq(EstadoOferta.RECHAZADA))).thenReturn(1);
        when(servicioService.crearDesdeOferta(
                contexto.oferta(), contexto.solicitud())).thenReturn(servicio);

        RespuestaNegociacionResponse response = negociacionService.responder(
                contexto.clienteUsuarioId(),
                contexto.oferta().getId(),
                new ContraofertaRequest(AccionContraoferta.ACEPTAR, null, null));

        assertEquals(EstadoContraoferta.ACEPTADA, pendiente.getEstado());
        assertEquals(EstadoOferta.ACEPTADA, contexto.oferta().getEstado());
        assertEquals(EstadoSolicitud.ACEPTADA, contexto.solicitud().getEstado());
        assertEquals(300L, response.servicio().id());
        verify(servicioService).crearDesdeOferta(
                contexto.oferta(), contexto.solicitud());
        verify(notificacionService).crear(
                eq(contexto.tecnicoUsuarioId()),
                eq(TipoNotificacion.OFERTA_ACEPTADA),
                anyString(),
                anyString(),
                eq("SERVICIO"),
                eq(300L));
        verify(notificacionService).crear(
                eq(contexto.clienteUsuarioId()),
                eq(TipoNotificacion.OFERTA_ACEPTADA),
                anyString(),
                anyString(),
                eq("SERVICIO"),
                eq(300L));
        verify(notificacionService).crear(
                eq(30L),
                eq(TipoNotificacion.OFERTA_RECHAZADA),
                anyString(),
                anyString(),
                eq("OFERTA"),
                eq(200L));
    }

    @Test
    void rechazar_una_contraoferta_rechaza_la_oferta_y_notifica_al_emisor() {
        Contexto contexto = contexto(EstadoOferta.CONTRAOFERTADA);
        Contraoferta pendiente = contraoferta(
                contexto.oferta(), EmisorContraoferta.CLIENTE, 1,
                "80.00", EstadoContraoferta.PENDIENTE);
        contexto.oferta().getContraofertas().add(pendiente);
        prepararBloqueo(contexto);
        when(ofertaRepository.save(any(Oferta.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RespuestaNegociacionResponse response = negociacionService.responder(
                contexto.tecnicoUsuarioId(),
                contexto.oferta().getId(),
                new ContraofertaRequest(AccionContraoferta.RECHAZAR, null, null));

        assertEquals(EstadoContraoferta.RECHAZADA, pendiente.getEstado());
        assertEquals(EstadoOferta.RECHAZADA, response.oferta().estado());
        verify(notificacionService).crear(
                eq(contexto.clienteUsuarioId()),
                eq(TipoNotificacion.OFERTA_RECHAZADA),
                anyString(),
                anyString(),
                eq("OFERTA"),
                eq(contexto.oferta().getId()));
    }

    @Test
    void no_permite_aceptar_sin_contraoferta_pendiente() {
        Contexto contexto = contexto(EstadoOferta.ENVIADA);
        prepararBloqueo(contexto);

        assertThrows(
                BusinessException.class,
                () -> negociacionService.responder(
                        contexto.tecnicoUsuarioId(),
                        contexto.oferta().getId(),
                        new ContraofertaRequest(AccionContraoferta.ACEPTAR, null, null)));
    }

    @Test
    void no_permite_aceptar_su_propia_contraoferta() {
        Contexto contexto = contexto(EstadoOferta.CONTRAOFERTADA);
        contexto.oferta().getContraofertas().add(contraoferta(
                contexto.oferta(), EmisorContraoferta.CLIENTE, 1,
                "80.00", EstadoContraoferta.PENDIENTE));
        prepararBloqueo(contexto);

        assertThrows(
                BusinessException.class,
                () -> negociacionService.responder(
                        contexto.clienteUsuarioId(),
                        contexto.oferta().getId(),
                        new ContraofertaRequest(AccionContraoferta.ACEPTAR, null, null)));
    }

    @Test
    void el_cliente_acepta_una_oferta_directamente_y_se_crea_el_servicio() {
        Contexto contexto = contexto(EstadoOferta.ENVIADA);
        Servicio servicio = servicio(contexto, 300L);

        prepararBloqueo(contexto);
        when(ofertaRepository.save(any(Oferta.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(solicitudRepository.save(any(Solicitud.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(ofertaRepository.buscarOtrasActivas(
                eq(contexto.solicitud().getId()),
                eq(contexto.oferta().getId()),
                any())).thenReturn(List.of());
        when(servicioService.crearDesdeOferta(
                contexto.oferta(), contexto.solicitud())).thenReturn(servicio);

        ServicioResponse response = negociacionService.aceptar(
                contexto.clienteUsuarioId(), contexto.oferta().getId());

        assertEquals(300L, response.id());
        assertEquals(EstadoOferta.ACEPTADA, contexto.oferta().getEstado());
        assertEquals(EstadoSolicitud.ACEPTADA, contexto.solicitud().getEstado());
        verify(ofertaRepository).rechazarDemas(
                eq(contexto.solicitud().getId()),
                eq(contexto.oferta().getId()),
                any(),
                eq(EstadoOferta.RECHAZADA));
    }

    @Test
    void solo_el_cliente_dueno_puede_aceptar_la_oferta() {
        Contexto contexto = contexto(EstadoOferta.ENVIADA);
        prepararBloqueo(contexto);

        assertThrows(
                ForbiddenException.class,
                () -> negociacionService.aceptar(99L, contexto.oferta().getId()));
        verify(servicioService, never()).crearDesdeOferta(any(), any());
    }

    @Test
    void no_permite_aceptar_la_oferta_si_hay_contraoferta_pendiente() {
        Contexto contexto = contexto(EstadoOferta.CONTRAOFERTADA);
        contexto.oferta().getContraofertas().add(contraoferta(
                contexto.oferta(), EmisorContraoferta.TECNICO, 1,
                "90.00", EstadoContraoferta.PENDIENTE));
        prepararBloqueo(contexto);

        assertThrows(
                BusinessException.class,
                () -> negociacionService.aceptar(
                        contexto.clienteUsuarioId(), contexto.oferta().getId()));
        verify(servicioService, never()).crearDesdeOferta(any(), any());
    }

    @Test
    void no_permite_aceptar_una_oferta_que_ya_no_esta_activa() {
        Contexto contexto = contexto(EstadoOferta.RECHAZADA);
        prepararBloqueo(contexto);

        assertThrows(
                BusinessException.class,
                () -> negociacionService.aceptar(
                        contexto.clienteUsuarioId(), contexto.oferta().getId()));
    }

    private void prepararBloqueo(Contexto contexto) {
        when(ofertaRepository.findSolicitudIdPorOfertaId(contexto.oferta().getId()))
                .thenReturn(Optional.of(contexto.solicitud().getId()));
        when(solicitudRepository.findByIdForUpdate(contexto.solicitud().getId()))
                .thenReturn(Optional.of(contexto.solicitud()));
        when(ofertaRepository.findByIdForUpdate(contexto.oferta().getId()))
                .thenReturn(Optional.of(contexto.oferta()));
    }

    private Contexto contexto(EstadoOferta estadoOferta) {
        Cliente cliente = cliente(1L, 10L);
        Distrito distrito = distrito(1L, "Piura", "15.00");
        Solicitud solicitud = solicitud(50L, cliente, distrito);
        Tecnico tecnico = tecnico(2L, 1L, distrito);
        Oferta oferta = oferta(100L, solicitud, tecnico, estadoOferta);
        return new Contexto(solicitud, oferta, cliente, tecnico, distrito);
    }

    private Servicio servicio(Contexto contexto, Long id) {
        return Servicio.builder()
                .id(id)
                .solicitud(contexto.solicitud())
                .oferta(contexto.oferta())
                .cliente(contexto.cliente())
                .tecnico(contexto.tecnico())
                .modalidad(Modalidad.DOMICILIO)
                .estado(EstadoServicio.PROGRAMADO)
                .precioServicio(new BigDecimal("100.00"))
                .costoMovilidad(new BigDecimal("10.00"))
                .costoDiagnostico(BigDecimal.ZERO)
                .montoTotal(new BigDecimal("110.00"))
                .garantiaDias(7)
                .build();
    }

    private Usuario usuario(Long id, Rol rol) {
        return Usuario.builder().id(id).rol(rol).build();
    }

    private Cliente cliente(Long id, Long usuarioId) {
        return Cliente.builder()
                .id(id)
                .usuario(usuario(usuarioId, Rol.CLIENTE))
                .nombres("Cliente")
                .apellidos("Prueba")
                .celular("999999999")
                .build();
    }

    private Distrito distrito(Long id, String nombre, String tope) {
        return Distrito.builder()
                .id(id)
                .nombre(nombre)
                .topeMovilidad(new BigDecimal(tope))
                .activo(true)
                .build();
    }

    private Tecnico tecnico(Long id, Long usuarioId, Distrito distrito) {
        return Tecnico.builder()
                .id(id)
                .usuario(usuario(usuarioId, Rol.TECNICO))
                .nombres("Tecnico")
                .apellidos("Prueba")
                .dni("12345678")
                .celular("988888888")
                .ofreceDomicilio(true)
                .ofreceTaller(true)
                .calificacionPromedio(new BigDecimal("4.50"))
                .distritos(Set.of(distrito))
                .build();
    }

    private Solicitud solicitud(Long id, Cliente cliente, Distrito distrito) {
        return Solicitud.builder()
                .id(id)
                .cliente(cliente)
                .distrito(distrito)
                .modalidad(Modalidad.DOMICILIO)
                .estado(EstadoSolicitud.CON_OFERTAS)
                .fechaVencimiento(FUTURO)
                .descripcion("Descripcion")
                .build();
    }

    private Oferta oferta(
            Long id, Solicitud solicitud, Tecnico tecnico, EstadoOferta estado) {
        return Oferta.builder()
                .id(id)
                .solicitud(solicitud)
                .tecnico(tecnico)
                .modalidad(Modalidad.DOMICILIO)
                .precioServicio(new BigDecimal("100.00"))
                .costoMovilidad(new BigDecimal("10.00"))
                .costoDiagnostico(BigDecimal.ZERO)
                .tiempoEstimadoHoras(2)
                .garantiaDias(7)
                .estado(estado)
                .contraofertas(new ArrayList<>())
                .build();
    }

    private Contraoferta contraoferta(
            Oferta oferta,
            EmisorContraoferta emisor,
            int ronda,
            String monto,
            EstadoContraoferta estado) {
        return Contraoferta.builder()
                .id((long) (ronda + 1000))
                .oferta(oferta)
                .emisor(emisor)
                .ronda(ronda)
                .montoPropuesto(new BigDecimal(monto))
                .estado(estado)
                .build();
    }

    private record Contexto(
            Solicitud solicitud,
            Oferta oferta,
            Cliente cliente,
            Tecnico tecnico,
            Distrito distrito) {

        Long clienteUsuarioId() {
            return cliente.getUsuario().getId();
        }

        Long tecnicoUsuarioId() {
            return tecnico.getUsuario().getId();
        }
    }
}
