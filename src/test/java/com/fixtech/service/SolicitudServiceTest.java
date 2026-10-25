package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fixtech.dto.SolicitudDisponibleResponse;
import com.fixtech.dto.SolicitudRequest;
import com.fixtech.dto.SolicitudResponse;
import com.fixtech.dto.SolicitudResumenResponse;
import com.fixtech.entity.Cliente;
import com.fixtech.entity.Distrito;
import com.fixtech.entity.ImagenSolicitud;
import com.fixtech.entity.Solicitud;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.TipoEquipo;
import com.fixtech.entity.TipoServicio;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoSolicitud;
import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.entity.enums.Rol;
import com.fixtech.entity.enums.Urgencia;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.repository.ClienteRepository;
import com.fixtech.repository.DistritoRepository;
import com.fixtech.repository.ImagenSolicitudRepository;
import com.fixtech.repository.SolicitudRepository;
import com.fixtech.repository.TecnicoRepository;
import com.fixtech.repository.TipoEquipoRepository;
import com.fixtech.repository.TipoServicioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class SolicitudServiceTest {

    @Mock
    private SolicitudRepository solicitudRepository;

    @Mock
    private ImagenSolicitudRepository imagenSolicitudRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private TecnicoRepository tecnicoRepository;

    @Mock
    private TipoEquipoRepository tipoEquipoRepository;

    @Mock
    private TipoServicioRepository tipoServicioRepository;

    @Mock
    private DistritoRepository distritoRepository;

    @Mock
    private ParametroSistemaService parametroSistemaService;

    @Mock
    private ArchivoService archivoService;

    @InjectMocks
    private SolicitudService solicitudService;

    @BeforeEach
    void configurarParametros() {
        lenient().when(parametroSistemaService.obtenerEntero(
                ParametroClave.MAX_IMAGENES_SOLICITUD)).thenReturn(5);
        lenient().when(parametroSistemaService.obtenerEntero(
                ParametroClave.VENCIMIENTO_SOLICITUD_HORAS)).thenReturn(72);
    }

    @Test
    void crea_solicitud_con_foto_y_estado_publicada() {
        Cliente cliente = cliente(1L, 1L);
        TipoEquipo tipoEquipo = tipoEquipo(1L, "Laptop");
        TipoServicio tipoServicio = tipoServicio(1L, "Reparacion de hardware");
        Distrito distrito = distrito(1L, "Piura");

        when(clienteRepository.findByUsuarioId(1L)).thenReturn(Optional.of(cliente));
        when(tipoEquipoRepository.findById(1L)).thenReturn(Optional.of(tipoEquipo));
        when(tipoServicioRepository.findById(1L)).thenReturn(Optional.of(tipoServicio));
        when(distritoRepository.findById(1L)).thenReturn(Optional.of(distrito));
        when(solicitudRepository.save(any(Solicitud.class))).thenAnswer(invocation -> {
            Solicitud solicitud = invocation.getArgument(0);
            solicitud.setId(10L);
            return solicitud;
        });
        when(archivoService.guardarImagenSolicitud(eq(10L), any(MultipartFile.class)))
                .thenReturn("solicitudes/10/foto.png");
        when(solicitudRepository.saveAndFlush(any(Solicitud.class))).thenAnswer(invocation -> {
            Solicitud solicitud = invocation.getArgument(0);
            solicitud.getImagenes().forEach(imagen -> imagen.setId(20L));
            return solicitud;
        });

        MockMultipartFile foto = fotoPng();
        SolicitudResponse response = solicitudService.crear(1L, request(), List.of(foto));

        assertEquals(10L, response.id());
        assertEquals(EstadoSolicitud.PUBLICADA, response.estado());
        assertEquals("Laptop", response.tipoEquipo());
        assertEquals("Piura", response.distrito());
        assertEquals("No enciende", response.descripcion());
        assertEquals(new BigDecimal("150.00"), response.presupuestoReferencial());
        assertEquals(1, response.imagenes().size());
        assertEquals("/api/solicitudes/10/imagenes/20", response.imagenes().get(0).url());
        assertTrue(response.fechaVencimiento().isAfter(LocalDateTime.now().plusHours(71)));
        assertNotNull(response.creadoEn());

        verify(archivoService).guardarImagenSolicitud(eq(10L), same(foto));
        verify(solicitudRepository).save(any(Solicitud.class));
        verify(solicitudRepository).saveAndFlush(any(Solicitud.class));
    }

    @Test
    void rechaza_solicitud_sin_fotos() {
        assertThrows(BadRequestException.class,
                () -> solicitudService.crear(1L, request(), List.of()));

        verifyNoInteractions(clienteRepository, tipoEquipoRepository, tipoServicioRepository,
                distritoRepository, archivoService);
        verify(solicitudRepository, never()).save(any(Solicitud.class));
    }

    @Test
    void rechaza_solicitud_con_mas_fotos_del_maximo() {
        List<MultipartFile> fotos = List.of(
                fotoPng(), fotoPng(), fotoPng(), fotoPng(), fotoPng(), fotoPng());

        assertThrows(BadRequestException.class,
                () -> solicitudService.crear(1L, request(), fotos));

        verifyNoInteractions(clienteRepository, archivoService);
    }

    @Test
    void rechaza_solicitud_a_domicilio_sin_direccion() {
        when(clienteRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(cliente(1L, 1L)));
        when(tipoEquipoRepository.findById(1L))
                .thenReturn(Optional.of(tipoEquipo(1L, "Laptop")));
        when(tipoServicioRepository.findById(1L))
                .thenReturn(Optional.of(tipoServicio(1L, "Reparacion de hardware")));
        when(distritoRepository.findById(1L))
                .thenReturn(Optional.of(distrito(1L, "Piura")));

        SolicitudRequest request = new SolicitudRequest(
                1L, 1L, "Lenovo", "ThinkPad", "No enciende", Urgencia.ALTA,
                Modalidad.DOMICILIO, 1L, " ", "Frente al parque",
                LocalDate.now().plusDays(1), "9-12", new BigDecimal("150.00"));

        assertThrows(BadRequestException.class,
                () -> solicitudService.crear(1L, request, List.of(fotoPng())));

        verify(solicitudRepository, never()).save(any(Solicitud.class));
        verifyNoInteractions(archivoService);
    }

    @Test
    void actualizar_rechaza_solicitud_de_otro_cliente() {
        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(
                solicitud(10L, cliente(1L, 99L), EstadoSolicitud.PUBLICADA,
                        Modalidad.DOMICILIO, distrito(1L, "Piura"))));

        assertThrows(ForbiddenException.class,
                () -> solicitudService.actualizar(1L, 10L, request()));

        verify(solicitudRepository, never()).save(any(Solicitud.class));
    }

    @Test
    void actualizar_rechaza_solicitud_que_no_esta_publicada() {
        Solicitud solicitud = solicitud(10L, cliente(1L, 1L), EstadoSolicitud.CON_OFERTAS,
                Modalidad.DOMICILIO, distrito(1L, "Piura"));
        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(solicitud));

        assertThrows(BusinessException.class,
                () -> solicitudService.actualizar(1L, 10L, request()));

        verifyNoInteractions(tipoEquipoRepository, tipoServicioRepository, distritoRepository);
        verify(solicitudRepository, never()).save(any(Solicitud.class));
    }

    @Test
    void actualizar_modifica_campos_de_solicitud_publicada() {
        Solicitud solicitud = solicitud(10L, cliente(1L, 1L), EstadoSolicitud.PUBLICADA,
                Modalidad.DOMICILIO, distrito(1L, "Piura"));
        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(solicitud));
        when(tipoEquipoRepository.findById(2L))
                .thenReturn(Optional.of(tipoEquipo(2L, "PC de escritorio")));
        when(tipoServicioRepository.findById(2L))
                .thenReturn(Optional.of(tipoServicio(2L, "Diagnostico")));
        when(distritoRepository.findById(2L))
                .thenReturn(Optional.of(distrito(2L, "Castilla")));
        when(solicitudRepository.save(solicitud)).thenReturn(solicitud);

        SolicitudRequest request = new SolicitudRequest(
                2L, 2L, "HP", "Pavilion", " Actualizada ", Urgencia.BAJA,
                Modalidad.TALLER, 2L, null, null,
                LocalDate.now().plusDays(2), "10-13", new BigDecimal("99.999"));

        SolicitudResponse response = solicitudService.actualizar(1L, 10L, request);

        assertEquals("PC de escritorio", response.tipoEquipo());
        assertEquals("Diagnostico", response.tipoServicio());
        assertEquals("Castilla", response.distrito());
        assertEquals("Actualizada", response.descripcion());
        assertEquals(Modalidad.TALLER, response.modalidad());
        assertNull(response.direccion());
        assertEquals(new BigDecimal("100.00"), response.presupuestoReferencial());
        verify(solicitudRepository).save(solicitud);
    }

    @Test
    void cancelar_marca_la_solicitud_y_guarda_el_motivo() {
        Solicitud solicitud = solicitud(10L, cliente(1L, 1L), EstadoSolicitud.PUBLICADA,
                Modalidad.DOMICILIO, distrito(1L, "Piura"));
        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(solicitud));
        when(solicitudRepository.save(solicitud)).thenReturn(solicitud);

        SolicitudResponse response = solicitudService.cancelar(
                1L, 10L, "Ya no lo necesito");

        assertEquals(EstadoSolicitud.CANCELADA, response.estado());
        assertEquals("Ya no lo necesito", response.motivoCancelacion());
        verify(solicitudRepository).save(solicitud);
    }

    @Test
    void cancelar_rechaza_solicitud_ya_aceptada() {
        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(
                solicitud(10L, cliente(1L, 1L), EstadoSolicitud.ACEPTADA,
                        Modalidad.DOMICILIO, distrito(1L, "Piura"))));

        assertThrows(BusinessException.class,
                () -> solicitudService.cancelar(1L, 10L, null));

        verify(solicitudRepository, never()).save(any(Solicitud.class));
    }

    @Test
    void cancelar_rechaza_motivo_demasiado_largo() {
        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(
                solicitud(10L, cliente(1L, 1L), EstadoSolicitud.PUBLICADA,
                        Modalidad.DOMICILIO, distrito(1L, "Piura"))));

        assertThrows(BadRequestException.class,
                () -> solicitudService.cancelar(1L, 10L, "x".repeat(301)));

        verify(solicitudRepository, never()).save(any(Solicitud.class));
    }

    @Test
    void lista_mis_solicitudes_con_imagen_principal() {
        Solicitud solicitud = solicitud(10L, cliente(1L, 1L), EstadoSolicitud.PUBLICADA,
                Modalidad.DOMICILIO, distrito(1L, "Piura"));
        when(clienteRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(cliente(1L, 1L)));
        when(solicitudRepository.findByClienteIdOrderByCreadoEnDesc(1L))
                .thenReturn(List.of(solicitud));

        List<SolicitudResumenResponse> response = solicitudService.listarMias(1L, null);

        assertEquals(1, response.size());
        assertEquals("/api/solicitudes/10/imagenes/20", response.get(0).imagenPrincipalUrl());
    }

    @Test
    void lista_mis_solicitudes_filtrando_por_estado() {
        Solicitud solicitud = solicitud(10L, cliente(1L, 1L), EstadoSolicitud.CANCELADA,
                Modalidad.DOMICILIO, distrito(1L, "Piura"));
        when(clienteRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(cliente(1L, 1L)));
        when(solicitudRepository.findByClienteIdAndEstadoOrderByCreadoEnDesc(
                1L, EstadoSolicitud.CANCELADA)).thenReturn(List.of(solicitud));

        List<SolicitudResumenResponse> response = solicitudService.listarMias(
                1L, EstadoSolicitud.CANCELADA);

        assertEquals(1, response.size());
        assertEquals(EstadoSolicitud.CANCELADA, response.get(0).estado());
    }

    @Test
    void lista_solicitudes_disponibles_por_distrito_y_modalidad() {
        Tecnico tecnico = tecnico(2L, 2L, true, false, distrito(1L, "Piura"));
        Solicitud solicitud = solicitud(10L, cliente(1L, 1L), EstadoSolicitud.PUBLICADA,
                Modalidad.DOMICILIO, distrito(1L, "Piura"));
        when(tecnicoRepository.findByUsuarioId(2L)).thenReturn(Optional.of(tecnico));
        when(solicitudRepository.buscarDisponibles(
                anyCollection(), any(LocalDateTime.class), anyCollection(), anyCollection()))
                .thenReturn(List.of(solicitud));

        List<SolicitudDisponibleResponse> response = solicitudService.listarDisponibles(2L);

        assertEquals(1, response.size());
        assertEquals("Piura", response.get(0).distrito());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<Long>> distritosCaptor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<Modalidad>> modalidadesCaptor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(solicitudRepository).buscarDisponibles(
                anyCollection(), any(LocalDateTime.class),
                distritosCaptor.capture(), modalidadesCaptor.capture());

        assertEquals(Set.of(1L), Set.copyOf(distritosCaptor.getValue()));
        assertTrue(modalidadesCaptor.getValue().containsAll(
                Set.of(Modalidad.DOMICILIO, Modalidad.INDISTINTO)));
    }

    @Test
    void lista_disponibles_vacia_si_el_tecnico_no_tiene_distritos() {
        Tecnico tecnico = tecnico(2L, 2L, true, false, null);
        when(tecnicoRepository.findByUsuarioId(2L)).thenReturn(Optional.of(tecnico));

        assertTrue(solicitudService.listarDisponibles(2L).isEmpty());

        verify(solicitudRepository, never()).buscarDisponibles(
                anyCollection(), any(LocalDateTime.class), anyCollection(), anyCollection());
    }

    @Test
    void obtener_disponible_rechaza_solicitud_de_otro_distrito() {
        Tecnico tecnico = tecnico(2L, 2L, true, false, distrito(2L, "Castilla"));
        Solicitud solicitud = solicitud(10L, cliente(1L, 1L), EstadoSolicitud.PUBLICADA,
                Modalidad.DOMICILIO, distrito(1L, "Piura"));
        when(tecnicoRepository.findByUsuarioId(2L)).thenReturn(Optional.of(tecnico));
        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(solicitud));

        assertThrows(ForbiddenException.class,
                () -> solicitudService.obtenerDisponible(2L, 10L));
    }

    @Test
    void obtener_disponible_permite_solicitud_compatible() {
        Tecnico tecnico = tecnico(2L, 2L, true, false, distrito(1L, "Piura"));
        Solicitud solicitud = solicitud(10L, cliente(1L, 1L), EstadoSolicitud.PUBLICADA,
                Modalidad.DOMICILIO, distrito(1L, "Piura"));
        when(tecnicoRepository.findByUsuarioId(2L)).thenReturn(Optional.of(tecnico));
        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(solicitud));

        SolicitudDisponibleResponse response = solicitudService.obtenerDisponible(2L, 10L);

        assertEquals(10L, response.id());
        assertEquals("No enciende", response.descripcion());
    }

    @Test
    void obtener_imagen_permite_al_cliente_dueno() {
        Solicitud solicitud = solicitud(10L, cliente(1L, 1L), EstadoSolicitud.PUBLICADA,
                Modalidad.DOMICILIO, distrito(1L, "Piura"));
        ImagenSolicitud imagen = solicitud.getImagenes().get(0);
        ArchivoService.RecursoImagen recurso = new ArchivoService.RecursoImagen(
                new ByteArrayResource(new byte[]{1, 2, 3}), MediaType.IMAGE_PNG);

        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(solicitud));
        when(imagenSolicitudRepository.findByIdAndSolicitudId(20L, 10L))
                .thenReturn(Optional.of(imagen));
        when(archivoService.cargar("solicitudes/10/foto.png")).thenReturn(recurso);

        ArchivoService.RecursoImagen response = solicitudService.obtenerImagen(1L, 10L, 20L);

        assertSame(recurso, response);
        verifyNoInteractions(tecnicoRepository);
    }

    @Test
    void obtener_imagen_rechaza_a_tecnico_incompatible() {
        Solicitud solicitud = solicitud(10L, cliente(1L, 1L), EstadoSolicitud.PUBLICADA,
                Modalidad.DOMICILIO, distrito(1L, "Piura"));
        Tecnico tecnico = tecnico(2L, 3L, true, false, distrito(2L, "Castilla"));
        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(solicitud));
        when(imagenSolicitudRepository.findByIdAndSolicitudId(20L, 10L))
                .thenReturn(Optional.of(solicitud.getImagenes().get(0)));
        when(tecnicoRepository.findByUsuarioId(3L)).thenReturn(Optional.of(tecnico));

        assertThrows(ForbiddenException.class,
                () -> solicitudService.obtenerImagen(3L, 10L, 20L));

        verifyNoInteractions(archivoService);
    }

    @Test
    void vence_solicitudes_publicadas_y_con_ofertas() {
        when(solicitudRepository.marcarVencidas(
                eq(EstadoSolicitud.VENCIDA), anyCollection(), any(LocalDateTime.class)))
                .thenReturn(3);

        assertEquals(3, solicitudService.vencerSolicitudes());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<EstadoSolicitud>> estadosCaptor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(solicitudRepository).marcarVencidas(
                eq(EstadoSolicitud.VENCIDA), estadosCaptor.capture(), any(LocalDateTime.class));
        assertEquals(Set.of(EstadoSolicitud.PUBLICADA, EstadoSolicitud.CON_OFERTAS),
                Set.copyOf(estadosCaptor.getValue()));
    }

    private SolicitudRequest request() {
        return new SolicitudRequest(
                1L, 1L, "Lenovo", "ThinkPad", " No enciende ", Urgencia.ALTA,
                Modalidad.DOMICILIO, 1L, "Av. Principal 123", "Frente al parque",
                LocalDate.now().plusDays(1), "9-12", new BigDecimal("150"));
    }

    private MockMultipartFile fotoPng() {
        return new MockMultipartFile(
                "fotos", "foto.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
    }

    private Cliente cliente(Long id, Long usuarioId) {
        return Cliente.builder()
                .id(id)
                .usuario(Usuario.builder()
                        .id(usuarioId)
                        .correo("cliente@fixtech.pe")
                        .rol(Rol.CLIENTE)
                        .build())
                .nombres("Ana")
                .apellidos("Perez")
                .celular("987654321")
                .build();
    }

    private TipoEquipo tipoEquipo(Long id, String nombre) {
        return TipoEquipo.builder().id(id).nombre(nombre).activo(true).build();
    }

    private TipoServicio tipoServicio(Long id, String nombre) {
        return TipoServicio.builder().id(id).nombre(nombre).activo(true).build();
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

    private Tecnico tecnico(
            Long id, Long usuarioId, boolean domicilio, boolean taller, Distrito distrito) {
        Tecnico tecnico = Tecnico.builder()
                .id(id)
                .usuario(Usuario.builder()
                        .id(usuarioId)
                        .correo("tecnico@fixtech.pe")
                        .rol(Rol.TECNICO)
                        .build())
                .nombres("Luis")
                .apellidos("Gomez")
                .dni("87654321")
                .celular("987654322")
                .ofreceDomicilio(domicilio)
                .ofreceTaller(taller)
                .estadoVerificacion(EstadoVerificacion.PENDIENTE)
                .build();
        if (distrito != null) {
            tecnico.getDistritos().add(distrito);
        }
        return tecnico;
    }

    private Solicitud solicitud(
            Long id, Cliente cliente, EstadoSolicitud estado, Modalidad modalidad, Distrito distrito) {
        Solicitud solicitud = Solicitud.builder()
                .id(id)
                .cliente(cliente)
                .tipoEquipo(tipoEquipo(1L, "Laptop"))
                .tipoServicio(tipoServicio(1L, "Reparacion de hardware"))
                .marca("Lenovo")
                .modelo("ThinkPad")
                .descripcion("No enciende")
                .urgencia(Urgencia.ALTA)
                .modalidad(modalidad)
                .distrito(distrito)
                .direccion("Av. Principal 123")
                .referencia("Frente al parque")
                .fechaPreferida(LocalDate.now().plusDays(1))
                .horarioPreferido("9-12")
                .presupuestoReferencial(new BigDecimal("150.00"))
                .estado(estado)
                .fechaVencimiento(LocalDateTime.now().plusHours(72))
                .build();
        solicitud.getImagenes().add(ImagenSolicitud.builder()
                .id(20L)
                .solicitud(solicitud)
                .rutaArchivo("solicitudes/10/foto.png")
                .orden((byte) 1)
                .build());
        return solicitud;
    }
}
