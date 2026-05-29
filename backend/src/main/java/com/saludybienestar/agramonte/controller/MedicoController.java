package com.saludybienestar.agramonte.controller;

import com.saludybienestar.agramonte.dto.response.MedicoResumenResponse;
import com.saludybienestar.agramonte.entity.Medico;
import com.saludybienestar.agramonte.repository.MedicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/medicos")
@RequiredArgsConstructor
public class MedicoController {

    private final MedicoRepository medicoRepository;

    @GetMapping
    public List<MedicoResumenResponse> listarMedicos(
            @RequestParam(required = false) String especialidad
    ) {
        List<Medico> medicos = (especialidad == null || especialidad.isBlank())
                ? medicoRepository.findAll()
                : medicoRepository.findByEspecialidad(especialidad);

        return medicos.stream()
                .map(m -> new MedicoResumenResponse(m.getId(), m.getNombre(), m.getEspecialidad()))
                .toList();
    }
}
