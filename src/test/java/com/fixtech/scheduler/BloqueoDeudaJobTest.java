package com.fixtech.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.service.DeudaTecnicoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BloqueoDeudaJobTest {

    @Mock
    private DeudaTecnicoService deudaTecnicoService;

    @InjectMocks
    private BloqueoDeudaJob job;

    @Test
    void evalua_las_deudas_de_los_tecnicos() {
        when(deudaTecnicoService.evaluarTodos()).thenReturn(2);

        job.evaluarDeudas();

        verify(deudaTecnicoService).evaluarTodos();
    }
}
