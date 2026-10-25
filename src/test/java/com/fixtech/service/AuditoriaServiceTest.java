package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.entity.Auditoria;
import com.fixtech.repository.AuditoriaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private AuditoriaRepository auditoriaRepository;

    @InjectMocks
    private AuditoriaService auditoriaService;

    @Test
    void registra_la_auditoria_normalizando_y_recortando_los_textos() {
        when(auditoriaRepository.save(any(Auditoria.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        auditoriaService.registrar(
                1L,
                "  VALIDAR_PAGO_COMISION  ",
                "  pago_comision  ",
                7L,
                "x".repeat(1200));

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(auditoriaRepository).save(captor.capture());
        Auditoria auditoria = captor.getValue();
        assertEquals(1L, auditoria.getUsuarioId());
        assertEquals("VALIDAR_PAGO_COMISION", auditoria.getAccion());
        assertEquals("pago_comision", auditoria.getEntidad());
        assertEquals(7L, auditoria.getEntidadId());
        assertEquals(1000, auditoria.getDetalle().length());
    }

    @Test
    void registra_una_auditoria_sin_detalle() {
        when(auditoriaRepository.save(any(Auditoria.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        auditoriaService.registrar(1L, "ACCION", "entidad", 7L, "   ");

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(auditoriaRepository).save(captor.capture());
        assertEquals("", captor.getValue().getDetalle());
    }

    @Test
    void registra_la_ip_de_la_solicitud_reenviada() {
        when(auditoriaRepository.save(any(Auditoria.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));
        try {
            auditoriaService.registrar(
                    1L, "ACCION", "entidad", 7L, "detalle");

            ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
            verify(auditoriaRepository).save(captor.capture());
            assertEquals("10.0.0.1", captor.getValue().getIp());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
