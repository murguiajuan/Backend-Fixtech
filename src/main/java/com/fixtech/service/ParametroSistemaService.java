package com.fixtech.service;

import com.fixtech.entity.ParametroSistema;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.repository.ParametroSistemaRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParametroSistemaService {

    private final ParametroSistemaRepository parametroSistemaRepository;

    public String obtenerTexto(ParametroClave clave) {
        return obtener(clave).getValor().trim();
    }

    public BigDecimal obtenerDecimal(ParametroClave clave) {
        String valor = obtenerTexto(clave);
        try {
            return new BigDecimal(valor);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "El parámetro '%s' no tiene un valor decimal válido".formatted(clave.getClave()), ex);
        }
    }

    public int obtenerEntero(ParametroClave clave) {
        String valor = obtenerTexto(clave);
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "El parámetro '%s' no tiene un valor entero válido".formatted(clave.getClave()), ex);
        }
    }

    private ParametroSistema obtener(ParametroClave clave) {
        return parametroSistemaRepository
                .findById(clave.getClave())
                .orElseThrow(() -> new IllegalStateException(
                        "El parámetro '%s' no está configurado".formatted(clave.getClave())));
    }
}
