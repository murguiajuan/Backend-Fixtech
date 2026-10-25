package com.fixtech.repository;

import com.fixtech.entity.TipoEquipo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoEquipoRepository extends JpaRepository<TipoEquipo, Long> {

    List<TipoEquipo> findByActivoTrueOrderByNombreAsc();

    List<TipoEquipo> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
