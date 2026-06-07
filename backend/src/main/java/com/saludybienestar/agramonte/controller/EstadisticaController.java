package com.saludybienestar.agramonte.controller;

import com.saludybienestar.agramonte.dto.response.EstadisticasResponse;
import com.saludybienestar.agramonte.service.EstadisticaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cuadro de mando de gestión (módulo SGE). Restringido por seguridad a los roles
 * MEDICO/ADMIN en {@code SecurityConfig}: las analíticas son una herramienta interna.
 */
@RestController
@RequestMapping("/api/estadisticas")
@RequiredArgsConstructor
public class EstadisticaController {

    private final EstadisticaService estadisticaService;

    @GetMapping
    public EstadisticasResponse estadisticas() {
        return estadisticaService.generar();
    }
}
