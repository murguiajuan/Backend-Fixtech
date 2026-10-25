package com.fixtech.scheduler;

import com.fixtech.service.DeudaTecnicoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BloqueoDeudaJob {

    private final DeudaTecnicoService deudaTecnicoService;

    @Scheduled(fixedDelayString = "${app.comisiones.deuda-intervalo-ms:60000}")
    public void evaluarDeudas() {
        int cambios = deudaTecnicoService.evaluarTodos();
        if (cambios > 0) {
            log.info("Se actualizó el bloqueo por deuda de {} técnicos", cambios);
        }
    }
}
