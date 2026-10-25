package com.fixtech.service;

import com.fixtech.dto.ParametroActualizacionRequest;
import com.fixtech.dto.ParametroResponse;
import com.fixtech.dto.ParametrosUpdateRequest;
import com.fixtech.entity.ParametroSistema;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ParametroSistemaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdministradorParametroService {

    private static final int MAX_PARAMETROS = 13;
    private static final BigDecimal TOPE_MONTO = new BigDecimal("1000000.00");
    private static final BigDecimal TOPE_CALIFICACION = new BigDecimal("5.00");

    private final ParametroSistemaRepository parametroSistemaRepository;
    private final AdministradorRepository administradorRepository;
    private final AuditoriaService auditoriaService;

    public List<ParametroResponse> listar() {
        return parametroSistemaRepository.findAll().stream()
                .sorted((a, b) -> a.getClave().compareToIgnoreCase(b.getClave()))
                .map(this::aResponse)
                .toList();
    }

    @Transactional
    public List<ParametroResponse> actualizar(
            Long adminUsuarioId, ParametrosUpdateRequest request) {
        validarAdministrador(adminUsuarioId);
        if (request == null || request.parametros() == null
                || request.parametros().isEmpty()) {
            throw new BadRequestException("Debe indicar al menos un parámetro");
        }
        if (request.parametros().size() > MAX_PARAMETROS) {
            throw new BadRequestException(
                    "No se pueden actualizar más de %d parámetros a la vez"
                            .formatted(MAX_PARAMETROS));
        }

        Map<ParametroClave, String> cambios = new LinkedHashMap<>();
        for (ParametroActualizacionRequest item : request.parametros()) {
            if (item == null) {
                throw new BadRequestException("Cada parámetro debe ser válido");
            }
            ParametroClave clave = ParametroClave.buscarPorClave(item.clave())
                    .orElseThrow(() -> new BadRequestException(
                            "Parámetro no permitido: " + item.clave()));
            if (cambios.containsKey(clave)) {
                throw new BadRequestException(
                        "El parámetro '%s' está repetido".formatted(clave.getClave()));
            }
            cambios.put(clave, validarValor(clave, item.valor()));
        }

        Map<String, ParametroSistema> existentes = new LinkedHashMap<>();
        for (ParametroSistema parametro : parametroSistemaRepository.findAll()) {
            existentes.put(parametro.getClave(), parametro);
        }

        for (Map.Entry<ParametroClave, String> cambio : cambios.entrySet()) {
            ParametroSistema parametro = existentes.get(cambio.getKey().getClave());
            if (parametro == null) {
                throw new NotFoundException(
                        "Parámetro no encontrado: " + cambio.getKey().getClave());
            }
            String anterior = parametro.getValor();
            String nuevo = cambio.getValue();
            if (nuevo.equals(anterior)) {
                continue;
            }
            parametro.setValor(nuevo);
            parametro.setActualizadoPorId(adminUsuarioId);
            parametroSistemaRepository.save(parametro);
            auditoriaService.registrar(
                    adminUsuarioId,
                    "ACTUALIZAR_PARAMETRO",
                    "parametro_sistema",
                    null,
                    "Parámetro %s: %s -> %s"
                            .formatted(parametro.getClave(), anterior, nuevo));
        }
        return listar();
    }

    private void validarAdministrador(Long adminUsuarioId) {
        administradorRepository.findByUsuarioId(adminUsuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de administrador"));
    }

    private String validarValor(ParametroClave clave, String valor) {
        if (!StringUtils.hasText(valor)) {
            throw new BadRequestException(
                    "El valor del parámetro '%s' es obligatorio"
                            .formatted(clave.getClave()));
        }
        String texto = valor.trim();
        return switch (clave) {
            case COMISION_PORCENTAJE -> decimal(
                    texto, clave, new BigDecimal("0.0001"), BigDecimal.ONE, 4);
            case LIMITE_DEUDA_COMISION -> decimal(
                    texto, clave, BigDecimal.ZERO, TOPE_MONTO, 2);
            case CALIFICACION_MINIMA_REVISION -> decimal(
                    texto, clave, BigDecimal.ZERO, TOPE_CALIFICACION, 2);
            case TOPE_MOVILIDAD_DEFECTO -> decimal(
                    texto, clave, BigDecimal.ZERO, TOPE_MONTO, 2);
            case PLAZO_PAGO_COMISION_DIAS -> entero(texto, clave, 1, 365);
            case VENCIMIENTO_SOLICITUD_HORAS -> entero(texto, clave, 1, 720);
            case CONFIRMACION_AUTOMATICA_HORAS -> entero(texto, clave, 1, 720);
            case MAX_RONDAS_CONTRAOFERTA -> entero(texto, clave, 1, 20);
            case GARANTIA_MINIMA_DIAS -> entero(texto, clave, 0, 3650);
            case MAX_IMAGENES_SOLICITUD -> entero(texto, clave, 1, 5);
            case DIAS_PARA_RESENAR -> entero(texto, clave, 1, 3650);
            case MAX_CANCELACIONES_TECNICO_30D -> entero(texto, clave, 0, 1000);
            case MIN_RESENAS_REVISION -> entero(texto, clave, 0, 100000);
        };
    }

    private String decimal(
            String texto,
            ParametroClave clave,
            BigDecimal minimo,
            BigDecimal maximo,
            int escala) {
        BigDecimal valor;
        try {
            valor = new BigDecimal(texto);
        } catch (NumberFormatException ex) {
            throw new BadRequestException(
                    "El parámetro '%s' debe ser un número decimal"
                            .formatted(clave.getClave()));
        }
        if (valor.compareTo(minimo) < 0 || valor.compareTo(maximo) > 0) {
            throw new BadRequestException(
                    "El parámetro '%s' debe estar entre %s y %s"
                            .formatted(clave.getClave(),
                                    minimo.toPlainString(), maximo.toPlainString()));
        }
        return valor.setScale(escala, RoundingMode.HALF_UP).toPlainString();
    }

    private String entero(
            String texto, ParametroClave clave, int minimo, int maximo) {
        int valor;
        try {
            valor = Integer.parseInt(texto);
        } catch (NumberFormatException ex) {
            throw new BadRequestException(
                    "El parámetro '%s' debe ser un número entero"
                            .formatted(clave.getClave()));
        }
        if (valor < minimo || valor > maximo) {
            throw new BadRequestException(
                    "El parámetro '%s' debe estar entre %d y %d"
                            .formatted(clave.getClave(), minimo, maximo));
        }
        return Integer.toString(valor);
    }

    private ParametroResponse aResponse(ParametroSistema parametro) {
        return new ParametroResponse(
                parametro.getClave(),
                parametro.getValor(),
                parametro.getDescripcion(),
                parametro.getActualizadoEn(),
                parametro.getActualizadoPorId());
    }
}
