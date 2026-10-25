package com.fixtech.repository;

import com.fixtech.entity.HistorialEstadoServicio;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistorialEstadoServicioRepository
        extends JpaRepository<HistorialEstadoServicio, Long> {

    @EntityGraph(attributePaths = {"cambiadoPor"})
    List<HistorialEstadoServicio> findByServicioIdOrderByCreadoEnAscIdAsc(Long servicioId);
}
