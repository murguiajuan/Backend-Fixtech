package com.fixtech.repository;

import com.fixtech.entity.AvanceServicio;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvanceServicioRepository extends JpaRepository<AvanceServicio, Long> {

    List<AvanceServicio> findByServicioIdOrderByCreadoEnAscIdAsc(Long servicioId);

    Optional<AvanceServicio> findByIdAndServicioId(Long id, Long servicioId);
}
