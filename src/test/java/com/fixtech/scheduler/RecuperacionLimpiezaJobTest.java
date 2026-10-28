package com.fixtech.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.repository.TokenRecuperacionRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecuperacionLimpiezaJobTest {

    @Mock
    private TokenRecuperacionRepository tokenRecuperacionRepository;

    @InjectMocks
    private RecuperacionLimpiezaJob job;

    @Test
    void elimina_los_codigos_anteriores_a_un_dia() {
        when(tokenRecuperacionRepository.eliminarAnteriores(any(LocalDateTime.class)))
                .thenReturn(2);

        job.limpiar();

        verify(tokenRecuperacionRepository).eliminarAnteriores(any(LocalDateTime.class));
    }
}
