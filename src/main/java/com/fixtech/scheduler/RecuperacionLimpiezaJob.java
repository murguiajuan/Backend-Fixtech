package com.fixtech.scheduler;

import com.fixtech.repository.TokenRecuperacionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecuperacionLimpiezaJob {

    private final TokenRecuperacionRepository tokenRecuperacionRepository;

    @Scheduled(fixedDelayString = "${app.recuperacion.limpieza-intervalo-ms:3600000}")
    @Transactional
    public void limpiar() {
        int eliminados = tokenRecuperacionRepository.eliminarAnteriores(
                LocalDateTime.now().minusDays(1));
        if (eliminados > 0) {
            log.info("Se eliminaron {} códigos de recuperación antiguos", eliminados);
        }
    }
}
