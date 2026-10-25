package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fixtech.dto.ParametroActualizacionRequest;
import com.fixtech.dto.ParametroResponse;
import com.fixtech.dto.ParametrosUpdateRequest;
import com.fixtech.entity.Administrador;
import com.fixtech.entity.ParametroSistema;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.Rol;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ParametroSistemaRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdministradorParametroServiceTest {

    @Mock
    private ParametroSistemaRepository parametroSistemaRepository;

    @Mock
    private AdministradorRepository administradorRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private AdministradorParametroService administradorParametroService;

    @Test
    void lista_los_parametros_ordenados_por_clave() {
        when(parametroSistemaRepository.findAll()).thenReturn(List.of(
                parametro("limite_deuda_comision", "30.00"),
                parametro("comision_porcentaje", "0.0600")));

        List<ParametroResponse> respuesta = administradorParametroService.listar();

        assertEquals(2, respuesta.size());
        assertEquals("comision_porcentaje", respuesta.get(0).clave());
        assertEquals("limite_deuda_comision", respuesta.get(1).clave());
    }

    @Test
    void actualiza_los_parametros_y_audita_el_valor_anterior_y_nuevo() {
        conAdministrador();
        ParametroSistema comision = parametro("comision_porcentaje", "0.0600");
        ParametroSistema limite = parametro("limite_deuda_comision", "30.00");
        when(parametroSistemaRepository.findAll())
                .thenReturn(List.of(limite, comision));

        List<ParametroResponse> respuesta = administradorParametroService.actualizar(
                1L,
                new ParametrosUpdateRequest(List.of(
                        new ParametroActualizacionRequest(
                                " COMISION_PORCENTAJE ", "0.08"),
                        new ParametroActualizacionRequest(
                                "limite_deuda_comision", "35"))));

        assertEquals("0.0800", comision.getValor());
        assertEquals("35.00", limite.getValor());
        assertEquals(1L, comision.getActualizadoPorId());
        assertEquals("0.0800", respuesta.get(0).valor());
        assertEquals("35.00", respuesta.get(1).valor());
        verify(parametroSistemaRepository, times(2))
                .save(any(ParametroSistema.class));
        verify(auditoriaService, times(2)).registrar(
                eq(1L),
                eq("ACTUALIZAR_PARAMETRO"),
                eq("parametro_sistema"),
                isNull(),
                anyString());
    }

    @Test
    void no_guarda_ni_audita_cuando_el_valor_no_cambia() {
        conAdministrador();
        ParametroSistema limite = parametro("limite_deuda_comision", "30.00");
        when(parametroSistemaRepository.findAll()).thenReturn(List.of(limite));

        List<ParametroResponse> respuesta = administradorParametroService.actualizar(
                1L,
                new ParametrosUpdateRequest(List.of(
                        new ParametroActualizacionRequest(
                                "limite_deuda_comision", "30.00"))));

        assertEquals(1, respuesta.size());
        verify(parametroSistemaRepository, never()).save(any(ParametroSistema.class));
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void falla_si_el_parametro_no_existe_en_la_base() {
        conAdministrador();
        when(parametroSistemaRepository.findAll()).thenReturn(List.of());

        assertThrows(NotFoundException.class,
                () -> administradorParametroService.actualizar(
                        1L,
                        new ParametrosUpdateRequest(List.of(
                                new ParametroActualizacionRequest(
                                        "limite_deuda_comision", "35")))));
    }

    @Test
    void valida_los_parametros_recibidos() {
        conAdministrador();
        ParametroActualizacionRequest valido =
                new ParametroActualizacionRequest("limite_deuda_comision", "30.00");

        assertThrows(BadRequestException.class,
                () -> administradorParametroService.actualizar(1L, null));
        assertThrows(BadRequestException.class,
                () -> administradorParametroService.actualizar(
                        1L, new ParametrosUpdateRequest(List.of())));
        assertThrows(BadRequestException.class,
                () -> administradorParametroService.actualizar(
                        1L,
                        new ParametrosUpdateRequest(
                                Collections.nCopies(14, valido))));
        assertThrows(BadRequestException.class,
                () -> administradorParametroService.actualizar(
                        1L,
                        new ParametrosUpdateRequest(
                                Arrays.asList((ParametroActualizacionRequest) null))));
        assertThrows(BadRequestException.class,
                () -> administradorParametroService.actualizar(
                        1L,
                        new ParametrosUpdateRequest(List.of(valido, valido))));
        assertThrows(BadRequestException.class,
                () -> administradorParametroService.actualizar(
                        1L,
                        new ParametrosUpdateRequest(List.of(
                                new ParametroActualizacionRequest(
                                        "clave_inexistente", "1")))));
        assertThrows(BadRequestException.class,
                () -> administradorParametroService.actualizar(
                        1L,
                        new ParametrosUpdateRequest(List.of(
                                new ParametroActualizacionRequest(
                                        null, "1")))));
        assertThrows(BadRequestException.class,
                () -> administradorParametroService.actualizar(
                        1L,
                        new ParametrosUpdateRequest(List.of(
                                new ParametroActualizacionRequest(
                                        "limite_deuda_comision", "   ")))));
        assertThrows(BadRequestException.class,
                () -> administradorParametroService.actualizar(
                        1L,
                        new ParametrosUpdateRequest(List.of(
                                new ParametroActualizacionRequest(
                                        "comision_porcentaje", "abc")))));
        assertThrows(BadRequestException.class,
                () -> administradorParametroService.actualizar(
                        1L,
                        new ParametrosUpdateRequest(List.of(
                                new ParametroActualizacionRequest(
                                        "comision_porcentaje", "2")))));
        assertThrows(BadRequestException.class,
                () -> administradorParametroService.actualizar(
                        1L,
                        new ParametrosUpdateRequest(List.of(
                                new ParametroActualizacionRequest(
                                        "plazo_pago_comision_dias", "abc")))));
        assertThrows(BadRequestException.class,
                () -> administradorParametroService.actualizar(
                        1L,
                        new ParametrosUpdateRequest(List.of(
                                new ParametroActualizacionRequest(
                                        "plazo_pago_comision_dias", "0")))));

        verify(parametroSistemaRepository, never()).findAll();
    }

    @Test
    void exige_perfil_de_administrador() {
        when(administradorRepository.findByUsuarioId(9L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> administradorParametroService.actualizar(
                        9L,
                        new ParametrosUpdateRequest(List.of(
                                new ParametroActualizacionRequest(
                                        "limite_deuda_comision", "35")))));
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

    private ParametroSistema parametro(String clave, String valor) {
        return ParametroSistema.builder()
                .clave(clave)
                .valor(valor)
                .descripcion("Parámetro " + clave)
                .build();
    }
}
