package com.fixtech.dto;

public record ContactoClienteResponse(
        Long id,
        String nombres,
        String apellidos,
        String celular,
        String correo) {
}
