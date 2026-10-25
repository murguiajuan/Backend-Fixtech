package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fixtech.entity.ParametroSistema;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.repository.ParametroSistemaRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParametroSistemaServiceTest {

    @Mock
    private ParametroSistemaRepository parametroSistemaRepository;

    @InjectMocks
    private ParametroSistemaService parametroSistemaService;

    @Test
    void devuelve_el_valor_texto_del_parametro() {
        when(parametroSistemaRepository.findById(ParametroClave.COMISION_PORCENTAJE.getClave()))
                .thenReturn(Optional.of(parametro("0.0600")));

        String valor = parametroSistemaService.obtenerTexto(ParametroClave.COMISION_PORCENTAJE);

        assertEquals("0.0600", valor);
    }

    @Test
    void devuelve_el_porcentaje_de_comision_como_decimal() {
        when(parametroSistemaRepository.findById(ParametroClave.COMISION_PORCENTAJE.getClave()))
                .thenReturn(Optional.of(parametro("0.0600")));

        BigDecimal valor = parametroSistemaService.obtenerDecimal(ParametroClave.COMISION_PORCENTAJE);

        assertEquals(0, new BigDecimal("0.0600").compareTo(valor));
    }

    @Test
    void devuelve_el_plazo_de_pago_como_entero() {
        when(parametroSistemaRepository.findById(ParametroClave.PLAZO_PAGO_COMISION_DIAS.getClave()))
                .thenReturn(Optional.of(parametro("7")));

        int valor = parametroSistemaService.obtenerEntero(ParametroClave.PLAZO_PAGO_COMISION_DIAS);

        assertEquals(7, valor);
    }

    @Test
    void lanza_excepcion_si_el_parametro_no_existe() {
        when(parametroSistemaRepository.findById(ParametroClave.COMISION_PORCENTAJE.getClave()))
                .thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> parametroSistemaService.obtenerDecimal(ParametroClave.COMISION_PORCENTAJE));

        assertTrue(ex.getMessage().contains(ParametroClave.COMISION_PORCENTAJE.getClave()));
    }

    @Test
    void lanza_excepcion_si_el_valor_no_es_decimal() {
        when(parametroSistemaRepository.findById(ParametroClave.COMISION_PORCENTAJE.getClave()))
                .thenReturn(Optional.of(parametro("seis por ciento")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> parametroSistemaService.obtenerDecimal(ParametroClave.COMISION_PORCENTAJE));

        assertTrue(ex.getMessage().contains(ParametroClave.COMISION_PORCENTAJE.getClave()));
    }

    @Test
    void lanza_excepcion_si_el_valor_no_es_entero() {
        when(parametroSistemaRepository.findById(ParametroClave.PLAZO_PAGO_COMISION_DIAS.getClave()))
                .thenReturn(Optional.of(parametro("siete")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> parametroSistemaService.obtenerEntero(ParametroClave.PLAZO_PAGO_COMISION_DIAS));

        assertTrue(ex.getMessage().contains(ParametroClave.PLAZO_PAGO_COMISION_DIAS.getClave()));
    }

    private ParametroSistema parametro(String valor) {
        return ParametroSistema.builder().valor(valor).build();
    }
}
