package com.fixtech.scheduler;

import com.fixtech.service.OfertaService;
import com.fixtech.service.SolicitudService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VencimientoSolicitudesJob {

    private final SolicitudService solicitudService;
    private final OfertaService ofertaService;

    @Scheduled(fixedDelayString = "${app.solicitudes.vencimiento-intervalo-ms:60000}")
    public void vencerSolicitudes() {
        int vencidas = solicitudService.vencerSolicitudes();
        int ofertasVencidas = ofertaService.vencerOfertas();
        if (vencidas > 0) {
            log.info("Se marcaron {} solicitudes como vencidas", vencidas);
        }
        if (ofertasVencidas > 0) {
            log.info("Se marcaron {} ofertas como vencidas", ofertasVencidas);
        }
    }
}
