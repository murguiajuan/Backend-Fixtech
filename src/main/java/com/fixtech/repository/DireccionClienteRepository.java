package com.fixtech.repository;

import com.fixtech.entity.DireccionCliente;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DireccionClienteRepository extends JpaRepository<DireccionCliente, Long> {

    List<DireccionCliente> findByClienteIdOrderByPrincipalDesc(Long clienteId);
}
