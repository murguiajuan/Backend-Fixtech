package com.fixtech.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fixtech.config.JwtProperties;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtServiceTest {

    private JwtService jwtService;
    private Usuario usuario;

    @BeforeEach
    void configurar() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("secreto-de-prueba-con-mas-de-32-caracteres-1234567890");
        properties.setIssuer("https://fixtech.test");
        properties.setAccessTokenMinutos(15);
        properties.setRefreshTokenDias(7);
        jwtService = new JwtService(properties);

        usuario = Usuario.builder()
                .id(7L)
                .correo("cliente@fixtech.pe")
                .rol(Rol.CLIENTE)
                .build();
    }

    @Test
    void genera_un_access_token_con_el_id_y_rol_del_usuario() {
        String token = jwtService.generarAccessToken(usuario);

        Jwt jwt = jwtService.decodificarAccess(token);

        assertEquals("7", jwt.getSubject());
        assertEquals("CLIENTE", jwt.getClaimAsString("rol"));
        assertEquals("cliente@fixtech.pe", jwt.getClaimAsString("correo"));
        assertEquals("ACCESS", jwt.getClaimAsString("tipo"));
    }

    @Test
    void genera_un_refresh_token_valido_para_renovar() {
        String token = jwtService.generarRefreshToken(usuario);

        Jwt jwt = jwtService.decodificarRefresh(token);

        assertEquals("7", jwt.getSubject());
        assertEquals("REFRESH", jwt.getClaimAsString("tipo"));
    }

    @Test
    void rechaza_un_refresh_token_usado_como_access_token() {
        String token = jwtService.generarRefreshToken(usuario);

        assertThrows(JwtException.class, () -> jwtService.decodificarAccess(token));
    }

    @Test
    void rechaza_un_access_token_usado_como_refresh_token() {
        String token = jwtService.generarAccessToken(usuario);

        assertThrows(JwtException.class, () -> jwtService.decodificarRefresh(token));
    }

    @Test
    void rechaza_un_token_firmado_con_otro_secreto() {
        JwtProperties otrasPropiedades = new JwtProperties();
        otrasPropiedades.setSecret("otro-secreto-de-prueba-con-mas-de-32-caracteres-abcdef");
        otrasPropiedades.setIssuer("https://fixtech.test");
        JwtService otroServicio = new JwtService(otrasPropiedades);

        String token = otroServicio.generarAccessToken(usuario);

        assertThrows(JwtException.class, () -> jwtService.decodificarAccess(token));
    }

    @Test
    void devuelve_la_expiracion_del_access_token_en_segundos() {
        assertEquals(900L, jwtService.expiracionAccessSegundos());
    }
}
