package com.saludybienestar.agramonte.controller;

import com.saludybienestar.agramonte.dto.response.CentroResponse;
import com.saludybienestar.agramonte.repository.CentroRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/centros")
@RequiredArgsConstructor
public class CentroController {

    private final CentroRepository centroRepository;

    @GetMapping
    public List<CentroResponse> listarCentros() {
        return centroRepository.findAll().stream()
                .map(c -> new CentroResponse(
                        c.getId(), c.getCodigo(), c.getNombre(), c.getDireccion(), c.getCiudad()))
                .toList();
    }
}
