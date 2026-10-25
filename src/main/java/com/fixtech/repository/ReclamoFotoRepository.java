package com.fixtech.repository;

import com.fixtech.entity.ReclamoFoto;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReclamoFotoRepository extends JpaRepository<ReclamoFoto, Long> {

    Optional<ReclamoFoto> findByIdAndReclamoIdAndReclamoServicioId(
            Long id, Long reclamoId, Long servicioId);

    Optional<ReclamoFoto> findByIdAndReclamoId(Long id, Long reclamoId);
}
