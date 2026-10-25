package com.fixtech.repository;

import com.fixtech.entity.ConstanciaFoto;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConstanciaFotoRepository extends JpaRepository<ConstanciaFoto, Long> {

    Optional<ConstanciaFoto> findByIdAndConstanciaIdAndConstanciaServicioId(
            Long id, Long constanciaId, Long servicioId);
}
