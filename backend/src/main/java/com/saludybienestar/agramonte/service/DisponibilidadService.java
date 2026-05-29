package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.entity.Horario;
import com.saludybienestar.agramonte.entity.Medico;
import com.saludybienestar.agramonte.repository.CitaRepository;
import com.saludybienestar.agramonte.repository.HorarioRepository;
import com.saludybienestar.agramonte.repository.MedicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DisponibilidadService {

    private final MedicoRepository medicoRepository;
    private final HorarioRepository horarioRepository;
    private final CitaRepository citaRepository;

    /**
     * Devuelve los huecos libres para un médico en una fecha concreta.
     * Asume que los horarios están predefinidos en la tabla Horario (rango horario del médico).
     */
    public List<LocalDateTime> obtenerHuecosLibres(Long medicoId, String fecha) {
        LocalDate fechaDate = LocalDate.parse(fecha);  // espera formato "YYYY-MM-DD"
        LocalDateTime inicioDia = fechaDate.atStartOfDay();
        LocalDateTime finDia = fechaDate.atTime(LocalTime.MAX);

        // Obtener todos los horarios del médico para ese día (rango de horas)
        List<Horario> horariosDia = horarioRepository.findByMedicoIdAndInicioBetween(medicoId, inicioDia, finDia);
        if (horariosDia.isEmpty()) {
            return List.of();  // no trabaja ese día
        }

        // Obtener las horas ya ocupadas (citas confirmadas para ese médico y día)
        List<LocalDateTime> ocupadas = citaRepository.findHorasOcupadas(medicoId, inicioDia, finDia);

        // Filtrar los horarios disponibles (disponible = true) y que no estén en la lista de ocupadas
        return horariosDia.stream()
                .filter(Horario::isDisponible)
                .map(Horario::getInicio)
                .filter(inicio -> !ocupadas.contains(inicio))
                .sorted()
                .collect(Collectors.toList());
    }
}