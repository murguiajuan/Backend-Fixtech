package com.fixtech.repository;

import com.fixtech.entity.TipoServicio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoServicioRepository extends JpaRepository<TipoServicio, Long> {

    List<TipoServicio> findByActivoTrueOrderByNombreAsc();

    List<TipoServicio> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
