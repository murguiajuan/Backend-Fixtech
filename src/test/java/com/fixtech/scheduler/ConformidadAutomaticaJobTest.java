package com.fixtech.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.service.ConformidadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConformidadAutomaticaJobTest {

    @Mock
    private ConformidadService conformidadService;

    @InjectMocks
    private ConformidadAutomaticaJob job;

    @Test
    void ejecuta_la_confirmacion_de_servicios_vencidos() {
        when(conformidadService.confirmarVencidos()).thenReturn(3);

        job.confirmarServiciosVencidos();

        verify(conformidadService).confirmarVencidos();
    }
}
