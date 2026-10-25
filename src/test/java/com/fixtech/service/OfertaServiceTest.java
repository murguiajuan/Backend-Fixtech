package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.OfertaOrden;
import com.fixtech.dto.OfertaRequest;
import com.fixtech.dto.OfertaResponse;
import com.fixtech.entity.Cliente;
import com.fixtech.entity.Contraoferta;
import com.fixtech.entity.Distrito;
import com.fixtech.entity.Oferta;
import com.fixtech.entity.Solicitud;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EmisorContraoferta;
import com.fixtech.entity.enums.EstadoContraoferta;
import com.fixtech.entity.enums.EstadoOferta;
import com.fixtech.entity.enums.EstadoSolicitud;
import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.entity.enums.Rol;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.mapper.OfertaMapper;
import com.fixtech.repository.OfertaRepository;
import com.fixtech.repository.SolicitudRepository;
import com.fixtech.repository.TecnicoRepository;
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
class OfertaServiceTest {

    private static final LocalDateTime FUTURO = LocalDateTime.now().plusDays(1);

    @Mock
    private OfertaRepository ofertaRepository;

    @Mock
    private SolicitudRepository solicitudRepository;

    @Mock
    private TecnicoRepository tecnicoRepository;

    @Mock
    private ParametroSistemaService parametroSistemaService;

    @Mock
    private NotificacionService notificacionService;

    @Spy
    private OfertaMapper ofertaMapper = new OfertaMapper();

    @InjectMocks
    private OfertaService ofertaService;

    @Test
    void crea_oferta_valida_y_marca_la_solicitud_con_ofertas() {
        Cliente cliente = cliente(1L, 10L);
        Distrito distrito = distrito(1L, "Piura", "15.00");
        Solicitud solicitud = solicitud(50L, cliente, distrito, Modalidad.DOMICILIO);
        Tecnico tecnico = tecnico(2L, 1L, distrito);

        when(tecnicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(tecnico));
        when(solicitudRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(solicitud));
        when(ofertaRepository.findBySolicitudIdAndTecnicoId(50L, 2L))
                .thenReturn(Optional.empty());
        when(solicitudRepository.save(any(Solicitud.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(ofertaRepository.save(any(Oferta.class))).thenAnswer(invocation -> {
            Oferta oferta = invocation.getArgument(0);
            oferta.setId(100L);
            return oferta;
        });

        OfertaResponse response = ofertaService.crear(
                1L,
                50L,
                new OfertaRequest(
                        Modalidad.DOMICILIO,
                        new BigDecimal("100.00"),
                        new BigDecimal("10.00"),
                        new BigDecimal("5.00"),
                        2,
                        7,
                        "Mensaje"));

        assertEquals(100L, response.id());
        assertEquals(EstadoOferta.ENVIADA, response.estado());
        assertEquals(new BigDecimal("110.00"), response.precioTotal());
        assertEquals(new BigDecimal("5.00"), response.costoDiagnostico());
        assertEquals(EstadoSolicitud.CON_OFERTAS, solicitud.getEstado());
        verify(notificacionService).crear(
                eq(10L),
                eq(TipoNotificacion.OFERTA_RECIBIDA),
                anyString(),
                contains("110.00"),
                eq("OFERTA"),
                eq(100L));
    }

    @Test
    void rechaza_oferta_de_tecnico_no_aprobado() {
        Cliente cliente = cliente(1L, 10L);
        Distrito distrito = distrito(1L, "Piura", "15.00");
        Solicitud solicitud = solicitud(50L, cliente, distrito, Modalidad.DOMICILIO);
        Tecnico tecnico = tecnico(2L, 1L, distrito);
        tecnico.setEstadoVerificacion(EstadoVerificacion.PENDIENTE);

        when(tecnicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(tecnico));
        when(solicitudRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(solicitud));

        assertThrows(
                ForbiddenException.class,
                () -> ofertaService.crear(1L, 50L, requestValido()));
        verify(ofertaRepository, never()).save(any(Oferta.class));
    }

    @Test
    void rechaza_oferta_de_tecnico_bloqueado_por_deuda() {
        Cliente cliente = cliente(1L, 10L);
        Distrito distrito = distrito(1L, "Piura", "15.00");
        Solicitud solicitud = solicitud(50L, cliente, distrito, Modalidad.DOMICILIO);
        Tecnico tecnico = tecnico(2L, 1L, distrito);
        tecnico.setBloqueadoPorDeuda(true);

        when(tecnicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(tecnico));
        when(solicitudRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(solicitud));

        assertThrows(
                ForbiddenException.class,
                () -> ofertaService.crear(1L, 50L, requestValido()));
        verify(ofertaRepository, never()).save(any(Oferta.class));
    }

    @Test
    void rechaza_movilidad_superior_al_tope_del_distrito() {
        Cliente cliente = cliente(1L, 10L);
        Distrito distrito = distrito(1L, "Piura", "15.00");
        Solicitud solicitud = solicitud(50L, cliente, distrito, Modalidad.DOMICILIO);
        Tecnico tecnico = tecnico(2L, 1L, distrito);

        when(tecnicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(tecnico));
        when(solicitudRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(solicitud));

        OfertaRequest request = new OfertaRequest(
                Modalidad.DOMICILIO,
                new BigDecimal("100.00"),
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                2,
                7,
                null);

        assertThrows(
                BadRequestException.class,
                () -> ofertaService.crear(1L, 50L, request));
        verify(ofertaRepository, never()).save(any(Oferta.class));
    }

    @Test
    void rechaza_movilidad_en_modalidad_taller() {
        Cliente cliente = cliente(1L, 10L);
        Distrito distrito = distrito(1L, "Piura", "15.00");
        Solicitud solicitud = solicitud(50L, cliente, distrito, Modalidad.TALLER);
        Tecnico tecnico = tecnico(2L, 1L, distrito);

        when(tecnicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(tecnico));
        when(solicitudRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(solicitud));

        OfertaRequest request = new OfertaRequest(
                Modalidad.TALLER,
                new BigDecimal("100.00"),
                new BigDecimal("5.00"),
                BigDecimal.ZERO,
                2,
                7,
                null);

        assertThrows(
                BadRequestException.class,
                () -> ofertaService.crear(1L, 50L, request));
    }

    @Test
    void rechaza_modalidad_de_oferta_incompatible_con_la_solicitud() {
        Cliente cliente = cliente(1L, 10L);
        Distrito distrito = distrito(1L, "Piura", "15.00");
        Solicitud solicitud = solicitud(50L, cliente, distrito, Modalidad.DOMICILIO);
        Tecnico tecnico = tecnico(2L, 1L, distrito);

        when(tecnicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(tecnico));
        when(solicitudRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(solicitud));

        OfertaRequest request = new OfertaRequest(
                Modalidad.TALLER,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                2,
                7,
                null);

        assertThrows(
                BadRequestException.class,
                () -> ofertaService.crear(1L, 50L, request));
    }

    @Test
    void rechaza_segunda_oferta_activa_del_mismo_tecnico() {
        Cliente cliente = cliente(1L, 10L);
        Distrito distrito = distrito(1L, "Piura", "15.00");
        Solicitud solicitud = solicitud(50L, cliente, distrito, Modalidad.DOMICILIO);
        Tecnico tecnico = tecnico(2L, 1L, distrito);
        Oferta existente = oferta(100L, solicitud, tecnico, EstadoOferta.ENVIADA);

        when(tecnicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(tecnico));
        when(solicitudRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(solicitud));
        when(ofertaRepository.findBySolicitudIdAndTecnicoId(50L, 2L))
                .thenReturn(Optional.of(existente));

        assertThrows(
                BusinessException.class,
                () -> ofertaService.crear(1L, 50L, requestValido()));
        verify(ofertaRepository, never()).save(any(Oferta.class));
    }

    @Test
    void reactiva_una_oferta_retirada_y_limpia_la_negociacion_anterior() {
        Cliente cliente = cliente(1L, 10L);
        Distrito distrito = distrito(1L, "Piura", "15.00");
        Solicitud solicitud = solicitud(50L, cliente, distrito, Modalidad.DOMICILIO);
        solicitud.setEstado(EstadoSolicitud.CON_OFERTAS);
        Tecnico tecnico = tecnico(2L, 1L, distrito);
        Oferta existente = oferta(100L, solicitud, tecnico, EstadoOferta.RETIRADA);
        existente.getContraofertas().add(contraoferta(
                existente, EmisorContraoferta.CLIENTE, 1,
                new BigDecimal("80.00"), EstadoContraoferta.RECHAZADA));

        when(tecnicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(tecnico));
        when(solicitudRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(solicitud));
        when(ofertaRepository.findBySolicitudIdAndTecnicoId(50L, 2L))
                .thenReturn(Optional.of(existente));
        when(ofertaRepository.save(any(Oferta.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OfertaResponse response = ofertaService.crear(
                1L, 50L, requestValido());

        assertEquals(EstadoOferta.ENVIADA, response.estado());
        assertTrue(existente.getContraofertas().isEmpty());
        verify(notificacionService).crear(
                eq(10L),
                eq(TipoNotificacion.OFERTA_ACTUALIZADA),
                anyString(),
                anyString(),
                eq("OFERTA"),
                eq(100L));
    }

    @Test
    void editar_oferta_con_contraoferta_pendiente_la_supera_y_vuelve_a_enviada() {
        Cliente cliente = cliente(1L, 10L);
        Distrito distrito = distrito(1L, "Piura", "15.00");
        Solicitud solicitud = solicitud(50L, cliente, distrito, Modalidad.DOMICILIO);
        solicitud.setEstado(EstadoSolicitud.CON_OFERTAS);
        Tecnico tecnico = tecnico(2L, 1L, distrito);
        Oferta oferta = oferta(100L, solicitud, tecnico, EstadoOferta.CONTRAOFERTADA);
        Contraoferta pendiente = contraoferta(
                oferta, EmisorContraoferta.CLIENTE, 1,
                new BigDecimal("80.00"), EstadoContraoferta.PENDIENTE);
        oferta.getContraofertas().add(pendiente);

        when(ofertaRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(oferta));
        when(tecnicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(tecnico));
        when(ofertaRepository.save(any(Oferta.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OfertaResponse response = ofertaService.actualizar(
                1L,
                100L,
                new OfertaRequest(
                        Modalidad.DOMICILIO,
                        new BigDecimal("90.00"),
                        new BigDecimal("10.00"),
                        BigDecimal.ZERO,
                        3,
                        10,
                        "Actualizada"));

        assertEquals(EstadoOferta.ENVIADA, response.estado());
        assertEquals(new BigDecimal("100.00"), response.precioTotal());
        assertEquals(EstadoContraoferta.SUPERADA, pendiente.getEstado());
        verify(notificacionService).crear(
                eq(10L),
                eq(TipoNotificacion.OFERTA_ACTUALIZADA),
                anyString(),
                anyString(),
                eq("OFERTA"),
                eq(100L));
    }

    @Test
    void retira_una_oferta_activa_del_tecnico() {
        Cliente cliente = cliente(1L, 10L);
        Distrito distrito = distrito(1L, "Piura", "15.00");
        Solicitud solicitud = solicitud(50L, cliente, distrito, Modalidad.DOMICILIO);
        Tecnico tecnico = tecnico(2L, 1L, distrito);
        Oferta oferta = oferta(100L, solicitud, tecnico, EstadoOferta.ENVIADA);

        when(ofertaRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(oferta));
        when(tecnicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(tecnico));
        when(ofertaRepository.save(any(Oferta.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OfertaResponse response = ofertaService.retirar(1L, 100L);

        assertEquals(EstadoOferta.RETIRADA, response.estado());
        verify(notificacionService).crear(
                eq(10L),
                eq(TipoNotificacion.OFERTA_RETIRADA),
                anyString(),
                anyString(),
                eq("OFERTA"),
                eq(100L));
    }

    @Test
    void lista_ofertas_del_cliente_ordenadas_por_precio_y_excluye_retiradas() {
        Cliente cliente = cliente(1L, 10L);
        Distrito distrito = distrito(1L, "Piura", "15.00");
        Solicitud solicitud = solicitud(50L, cliente, distrito, Modalidad.DOMICILIO);

        Tecnico tecnicoA = tecnico(2L, 2L, distrito);
        tecnicoA.setCalificacionPromedio(new BigDecimal("4.50"));
        Tecnico tecnicoB = tecnico(3L, 3L, distrito);
        tecnicoB.setCalificacionPromedio(new BigDecimal("4.00"));
        Tecnico tecnicoC = tecnico(4L, 4L, distrito);
        tecnicoC.setCalificacionPromedio(new BigDecimal("4.80"));

        Oferta ofertaA = ofertaConPrecio(101L, solicitud, tecnicoA, "120.00");
        Oferta ofertaB = ofertaConPrecio(102L, solicitud, tecnicoB, "100.00");
        Oferta ofertaC = ofertaConPrecio(103L, solicitud, tecnicoC, "100.00");
        Oferta retirada = ofertaConPrecio(104L, solicitud, tecnicoA, "50.00");
        retirada.setEstado(EstadoOferta.RETIRADA);

        when(solicitudRepository.findById(50L)).thenReturn(Optional.of(solicitud));
        when(ofertaRepository.buscarPorSolicitud(50L))
                .thenReturn(List.of(ofertaA, ofertaB, ofertaC, retirada));

        List<OfertaResponse> response = ofertaService.listarDelCliente(
                10L, 50L, OfertaOrden.PRECIO);

        assertEquals(3, response.size());
        assertEquals(103L, response.get(0).id());
        assertEquals(102L, response.get(1).id());
        assertEquals(101L, response.get(2).id());
    }

    @Test
    void compara_hasta_tres_ofertas_respetando_el_orden_solicitado() {
        Cliente cliente = cliente(1L, 10L);
        Distrito distrito = distrito(1L, "Piura", "15.00");
        Solicitud solicitud = solicitud(50L, cliente, distrito, Modalidad.DOMICILIO);
        Tecnico tecnico = tecnico(2L, 1L, distrito);
        Oferta ofertaA = oferta(101L, solicitud, tecnico, EstadoOferta.ENVIADA);
        Oferta ofertaB = oferta(102L, solicitud, tecnico, EstadoOferta.ENVIADA);
        Oferta ofertaC = oferta(103L, solicitud, tecnico, EstadoOferta.ENVIADA);

        when(solicitudRepository.findById(50L)).thenReturn(Optional.of(solicitud));
        when(ofertaRepository.buscarDetallePorId(102L))
                .thenReturn(Optional.of(ofertaB));
        when(ofertaRepository.buscarDetallePorId(101L))
                .thenReturn(Optional.of(ofertaA));
        when(ofertaRepository.buscarDetallePorId(103L))
                .thenReturn(Optional.of(ofertaC));

        List<OfertaResponse> response = ofertaService.comparar(
                10L, 50L, List.of(102L, 101L, 103L));

        assertEquals(List.of(102L, 101L, 103L),
                response.stream().map(OfertaResponse::id).toList());
    }

    @Test
    void rechaza_comparacion_con_mas_de_tres_ofertas() {
        Cliente cliente = cliente(1L, 10L);
        Distrito distrito = distrito(1L, "Piura", "15.00");
        Solicitud solicitud = solicitud(50L, cliente, distrito, Modalidad.DOMICILIO);
        when(solicitudRepository.findById(50L)).thenReturn(Optional.of(solicitud));

        assertThrows(
                BadRequestException.class,
                () -> ofertaService.comparar(10L, 50L, List.of(1L, 2L, 3L, 4L)));
    }

    @Test
    void lista_las_ofertas_del_tecnico_autenticado() {
        Cliente cliente = cliente(1L, 10L);
        Distrito distrito = distrito(1L, "Piura", "15.00");
        Solicitud solicitud = solicitud(50L, cliente, distrito, Modalidad.DOMICILIO);
        Tecnico tecnico = tecnico(2L, 1L, distrito);
        Oferta oferta = oferta(100L, solicitud, tecnico, EstadoOferta.ENVIADA);

        when(tecnicoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(tecnico));
        when(ofertaRepository.buscarPorTecnico(2L)).thenReturn(List.of(oferta));

        List<OfertaResponse> response = ofertaService.listarMias(1L);

        assertEquals(1, response.size());
        assertEquals(100L, response.get(0).id());
    }

    @Test
    void marca_como_vencidas_las_ofertas_de_solicitudes_vencidas() {
        when(ofertaRepository.marcarExpiradasDeSolicitudes(
                eq(EstadoOferta.EXPIRADA),
                any(),
                eq(List.of(EstadoSolicitud.VENCIDA, EstadoSolicitud.CANCELADA))))
                .thenReturn(4);

        assertEquals(4, ofertaService.vencerOfertas());
    }

    private OfertaRequest requestValido() {
        return new OfertaRequest(
                Modalidad.DOMICILIO,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                2,
                7,
                "Mensaje");
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
                .estadoVerificacion(EstadoVerificacion.APROBADO)
                .ofreceDomicilio(true)
                .ofreceTaller(true)
                .calificacionPromedio(new BigDecimal("4.50"))
                .distritos(Set.of(distrito))
                .build();
    }

    private Solicitud solicitud(
            Long id, Cliente cliente, Distrito distrito, Modalidad modalidad) {
        return Solicitud.builder()
                .id(id)
                .cliente(cliente)
                .distrito(distrito)
                .modalidad(modalidad)
                .estado(EstadoSolicitud.PUBLICADA)
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

    private Oferta ofertaConPrecio(
            Long id, Solicitud solicitud, Tecnico tecnico, String precio) {
        Oferta oferta = oferta(id, solicitud, tecnico, EstadoOferta.ENVIADA);
        oferta.setPrecioServicio(new BigDecimal(precio));
        oferta.setCostoMovilidad(BigDecimal.ZERO);
        return oferta;
    }

    private Contraoferta contraoferta(
            Oferta oferta,
            EmisorContraoferta emisor,
            int ronda,
            BigDecimal monto,
            EstadoContraoferta estado) {
        return Contraoferta.builder()
                .id((long) (ronda + 1000))
                .oferta(oferta)
                .emisor(emisor)
                .ronda(ronda)
                .montoPropuesto(monto)
                .estado(estado)
                .build();
    }
}
