package com.fixtech.scheduler;

import com.fixtech.service.ConformidadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConformidadAutomaticaJob {

    private final ConformidadService conformidadService;

    @Scheduled(fixedDelayString = "${app.servicios.conformidad-intervalo-ms:60000}")
    public void confirmarServiciosVencidos() {
        int confirmados = conformidadService.confirmarVencidos();
        if (confirmados > 0) {
            log.info("Se confirmaron automáticamente {} servicios", confirmados);
        }
    }
}
