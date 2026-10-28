package com.fixtech.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fixtech.dto.RecuperacionVerificadaResponse;
import com.fixtech.dto.TokenResponse;
import com.fixtech.exception.GlobalExceptionHandler;
import com.fixtech.service.RecuperacionPasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RecuperacionPasswordControllerTest {

    @Mock
    private RecuperacionPasswordService recuperacionPasswordService;

    private MockMvc mockMvc;

    @BeforeEach
    void configurar() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RecuperacionPasswordController(recuperacionPasswordService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void solicitar_devuelve_202_y_un_mensaje_generico() throws Exception {
        mockMvc.perform(post("/api/auth/recuperacion/solicitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"cliente@fixtech.pe\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value(
                        "Si el correo está registrado, recibirás un código de recuperación."));

        verify(recuperacionPasswordService).solicitar(
                eq("cliente@fixtech.pe"), anyString());
    }

    @Test
    void verificar_devuelve_el_ticket_temporal() throws Exception {
        when(recuperacionPasswordService.verificar(
                eq("cliente@fixtech.pe"), eq("123456"), anyString()))
                .thenReturn(new RecuperacionVerificadaResponse("ticket", 600L));

        mockMvc.perform(post("/api/auth/recuperacion/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"cliente@fixtech.pe\",\"codigo\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").value("ticket"))
                .andExpect(jsonPath("$.expiraEnSegundos").value(600));
    }

    @Test
    void restablecer_devuelve_204() throws Exception {
        mockMvc.perform(post("/api/auth/recuperacion/restablecer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticket": "ticket-suficientemente-largo",
                                  "password": "NuevaPassword123"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(recuperacionPasswordService).restablecer(
                "ticket-suficientemente-largo", "NuevaPassword123");
    }

    @Test
    void acceder_devuelve_los_tokens() throws Exception {
        when(recuperacionPasswordService.acceder("ticket-suficientemente-largo"))
                .thenReturn(TokenResponse.bearer("access", "refresh", 900L));

        mockMvc.perform(post("/api/auth/recuperacion/acceder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"ticket-suficientemente-largo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiraEnSegundos").value(900));
    }

    @Test
    void verificar_rechaza_un_codigo_con_formato_invalido() throws Exception {
        mockMvc.perform(post("/api/auth/recuperacion/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"cliente@fixtech.pe\",\"codigo\":\"12ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Datos de entrada inválidos"));
    }

    @Test
    void solicitar_rechaza_un_correo_invalido() throws Exception {
        mockMvc.perform(post("/api/auth/recuperacion/solicitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"no-es-correo\"}"))
                .andExpect(status().isBadRequest());
    }
}
