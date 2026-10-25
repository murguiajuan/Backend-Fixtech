package com.fixtech.exception;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void configurar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void devuelve_404_cuando_no_existe_el_recurso() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/prueba/no-encontrado"))
                .andExpect(status().isNotFound())
                .andReturn();

        String cuerpo = cuerpo(resultado);
        assertTrue(cuerpo.contains("Solicitud no encontrada"));
        assertTrue(cuerpo.contains("\"estado\":404"));
    }

    @Test
    void devuelve_409_cuando_falla_una_regla_de_negocio() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/prueba/regla-de-negocio"))
                .andExpect(status().isConflict())
                .andReturn();

        assertTrue(cuerpo(resultado).contains("La oferta ya fue aceptada"));
    }

    @Test
    void devuelve_409_cuando_se_viola_una_restriccion_de_datos() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/prueba/conflicto-de-datos"))
                .andExpect(status().isConflict())
                .andReturn();

        assertTrue(cuerpo(resultado).contains("conflicto con datos existentes"));
    }

    @Test
    void devuelve_401_cuando_las_credenciales_son_invalidas() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/prueba/no-autorizado"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertTrue(cuerpo(resultado).contains("Credenciales inválidas"));
    }

    @Test
    void devuelve_403_cuando_la_cuenta_no_esta_activa() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/prueba/prohibido"))
                .andExpect(status().isForbidden())
                .andReturn();

        assertTrue(cuerpo(resultado).contains("La cuenta no está activa"));
    }

    @Test
    void devuelve_401_sin_exponer_el_detalle_del_token() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/prueba/token-invalido"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String cuerpo = cuerpo(resultado);
        assertTrue(cuerpo.contains("Token inválido o expirado"));
        assertFalse(cuerpo.contains("firma incorrecta"));
    }

    @Test
    void devuelve_400_cuando_la_validacion_falla() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/prueba/validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String cuerpo = cuerpo(resultado);
        assertTrue(cuerpo.contains("Datos de entrada inválidos"));
        assertTrue(cuerpo.contains("nombre"));
    }

    @Test
    void devuelve_400_cuando_el_cuerpo_no_es_json_valido() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/prueba/validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("no-es-json"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(cuerpo(resultado).contains("formato esperado"));
    }

    @Test
    void devuelve_403_cuando_el_rol_no_autoriza_la_operacion() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/prueba/acceso-denegado"))
                .andExpect(status().isForbidden())
                .andReturn();

        assertTrue(cuerpo(resultado).contains("No tiene permisos"));
    }

    @Test
    void devuelve_400_cuando_la_solicitud_es_invalida() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/prueba/solicitud-invalida"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(cuerpo(resultado).contains("Datos de entrada inválidos"));
    }

    @Test
    void devuelve_413_cuando_el_archivo_es_demasiado_grande() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/prueba/archivo-grande"))
                .andExpect(status().isPayloadTooLarge())
                .andReturn();

        assertTrue(cuerpo(resultado).contains("tamaño permitido"));
    }

    @Test
    void devuelve_500_sin_exponer_el_detalle_del_error() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/prueba/error"))
                .andExpect(status().isInternalServerError())
                .andReturn();

        String cuerpo = cuerpo(resultado);
        assertTrue(cuerpo.contains("Ocurrió un error inesperado"));
        assertFalse(cuerpo.contains("fallo interno"));
    }

    private String cuerpo(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @RestController
    static class ControladorDePrueba {

        @GetMapping("/prueba/no-encontrado")
        void noEncontrado() {
            throw new NotFoundException("Solicitud no encontrada");
        }

        @GetMapping("/prueba/regla-de-negocio")
        void reglaDeNegocio() {
            throw new BusinessException("La oferta ya fue aceptada");
        }

        @GetMapping("/prueba/conflicto-de-datos")
        void conflictoDeDatos() {
            throw new DataIntegrityViolationException("duplicado");
        }

        @GetMapping("/prueba/no-autorizado")
        void noAutorizado() {
            throw new UnauthorizedException("Credenciales inválidas");
        }

        @GetMapping("/prueba/prohibido")
        void prohibido() {
            throw new ForbiddenException("La cuenta no está activa");
        }

        @GetMapping("/prueba/solicitud-invalida")
        void solicitudInvalida() {
            throw new BadRequestException("Datos de entrada inválidos");
        }

        @GetMapping("/prueba/acceso-denegado")
        void accesoDenegado() {
            throw new AccessDeniedException("rol insuficiente");
        }

        @GetMapping("/prueba/archivo-grande")
        void archivoGrande() {
            throw new MaxUploadSizeExceededException(10L);
        }

        @GetMapping("/prueba/token-invalido")
        void tokenInvalido() {
            throw new JwtException("firma incorrecta");
        }

        @PostMapping("/prueba/validacion")
        void validacion(@Valid @RequestBody CuerpoDePrueba cuerpo) {
        }

        @GetMapping("/prueba/error")
        void error() {
            throw new IllegalStateException("fallo interno");
        }
    }

    record CuerpoDePrueba(@NotBlank String nombre) {
    }
}
