package com.fixtech.repository;

import com.fixtech.entity.ImagenSolicitud;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagenSolicitudRepository extends JpaRepository<ImagenSolicitud, Long> {

    Optional<ImagenSolicitud> findByIdAndSolicitudId(Long id, Long solicitudId);
}
