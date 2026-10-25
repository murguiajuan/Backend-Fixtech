package com.fixtech.dto;

import com.fixtech.entity.enums.Rol;

public record UsuarioRegistradoResponse(Long id, String correo, Rol rol) {
}
