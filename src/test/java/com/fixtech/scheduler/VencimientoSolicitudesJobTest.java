package com.fixtech.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.service.OfertaService;
import com.fixtech.service.SolicitudService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VencimientoSolicitudesJobTest {

    @Mock
    private SolicitudService solicitudService;

    @Mock
    private OfertaService ofertaService;

    @InjectMocks
    private VencimientoSolicitudesJob job;

    @Test
    void ejecuta_el_vencimiento_de_solicitudes_y_ofertas() {
        when(solicitudService.vencerSolicitudes()).thenReturn(2);
        when(ofertaService.vencerOfertas()).thenReturn(3);

        job.vencerSolicitudes();

        verify(solicitudService).vencerSolicitudes();
        verify(ofertaService).vencerOfertas();
    }
}
