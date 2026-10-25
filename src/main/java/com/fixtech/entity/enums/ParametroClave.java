package com.fixtech.entity.enums;

import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ParametroClave {

    COMISION_PORCENTAJE("comision_porcentaje"),
    LIMITE_DEUDA_COMISION("limite_deuda_comision"),
    PLAZO_PAGO_COMISION_DIAS("plazo_pago_comision_dias"),
    VENCIMIENTO_SOLICITUD_HORAS("vencimiento_solicitud_horas"),
    CONFIRMACION_AUTOMATICA_HORAS("confirmacion_automatica_horas"),
    MAX_RONDAS_CONTRAOFERTA("max_rondas_contraoferta"),
    GARANTIA_MINIMA_DIAS("garantia_minima_dias"),
    MAX_IMAGENES_SOLICITUD("max_imagenes_solicitud"),
    DIAS_PARA_RESENAR("dias_para_resenar"),
    MAX_CANCELACIONES_TECNICO_30D("max_cancelaciones_tecnico_30d"),
    CALIFICACION_MINIMA_REVISION("calificacion_minima_revision"),
    MIN_RESENAS_REVISION("min_resenas_revision"),
    TOPE_MOVILIDAD_DEFECTO("tope_movilidad_defecto");

    private final String clave;

    public static Optional<ParametroClave> buscarPorClave(String clave) {
        if (clave == null) {
            return Optional.empty();
        }
        String normalizada = clave.trim();
        for (ParametroClave parametro : values()) {
            if (parametro.clave.equalsIgnoreCase(normalizada)) {
                return Optional.of(parametro);
            }
        }
        return Optional.empty();
    }
}
