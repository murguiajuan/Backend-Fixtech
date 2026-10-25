package com.fixtech.repository;

import com.fixtech.entity.Distrito;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistritoRepository extends JpaRepository<Distrito, Long> {

    List<Distrito> findByActivoTrueOrderByNombreAsc();

    List<Distrito> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCaseAndProvinciaIgnoreCase(
            String nombre, String provincia);

    boolean existsByNombreIgnoreCaseAndProvinciaIgnoreCaseAndIdNot(
            String nombre, String provincia, Long id);
}
