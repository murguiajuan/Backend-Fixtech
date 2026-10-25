package com.fixtech.service;

import com.fixtech.entity.Comision;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.EstadoComision;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.exception.BusinessException;
import com.fixtech.repository.ComisionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ComisionService {

    private final ComisionRepository comisionRepository;
    private final ParametroSistemaService parametroSistemaService;
    private final DeudaTecnicoService deudaTecnicoService;

    @Transactional
    public Comision registrarParaServicio(Servicio servicio) {
        Comision comision = comisionRepository.findByServicioId(servicio.getId())
                .orElseGet(() -> crear(servicio));
        deudaTecnicoService.sincronizar(servicio.getTecnico());
        return comision;
    }

    private Comision crear(Servicio servicio) {
        BigDecimal base = servicio.getMontoFinal() == null
                ? servicio.getMontoTotal()
                : servicio.getMontoFinal();
        if (base == null || base.signum() < 0) {
            throw new BusinessException(
                    "El servicio no tiene un monto base válido para calcular la comisión");
        }
        base = base.setScale(2, RoundingMode.HALF_UP);

        BigDecimal porcentaje = parametroSistemaService.obtenerDecimal(
                        ParametroClave.COMISION_PORCENTAJE)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal montoComision = base.multiply(porcentaje)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal neto = base.subtract(montoComision)
                .setScale(2, RoundingMode.HALF_UP);
        int plazoDias = Math.max(0, parametroSistemaService.obtenerEntero(
                ParametroClave.PLAZO_PAGO_COMISION_DIAS));

        Comision comision = Comision.builder()
                .servicio(servicio)
                .tecnico(servicio.getTecnico())
                .montoBase(base)
                .porcentaje(porcentaje)
                .montoComision(montoComision)
                .montoNetoTecnico(neto)
                .estado(EstadoComision.PENDIENTE)
                .fechaLimitePago(LocalDate.now().plusDays(plazoDias))
                .build();
        return comisionRepository.save(comision);
    }
}
