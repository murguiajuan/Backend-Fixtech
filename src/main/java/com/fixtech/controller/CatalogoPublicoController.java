package com.fixtech.controller;

import com.fixtech.dto.CatalogoSimpleResponse;
import com.fixtech.dto.DistritoResponse;
import com.fixtech.dto.EspecialidadResponse;
import com.fixtech.service.CatalogoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalogos")
@RequiredArgsConstructor
public class CatalogoPublicoController {

    private final CatalogoService catalogoService;

    @GetMapping("/tipos-equipo")
    public List<CatalogoSimpleResponse> tiposEquipo() {
        return catalogoService.listarTiposEquipo(true);
    }

    @GetMapping("/tipos-servicio")
    public List<CatalogoSimpleResponse> tiposServicio() {
        return catalogoService.listarTiposServicio(true);
    }

    @GetMapping("/especialidades")
    public List<EspecialidadResponse> especialidades() {
        return catalogoService.listarEspecialidades(true);
    }

    @GetMapping("/distritos")
    public List<DistritoResponse> distritos() {
        return catalogoService.listarDistritos(true);
    }
}
