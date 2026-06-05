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
     * Devuelve los huecos libres para un médico en una fecha concreta (todos los centros).
     */
    public List<LocalDateTime> obtenerHuecosLibres(Long medicoId, String fecha) {
        return obtenerHuecosLibres(medicoId, fecha, null);
    }

    /**
     * Devuelve los huecos libres para un médico en una fecha concreta.
     * Si {@code centroCodigo} no es nulo/vacío, solo devuelve los huecos de ese centro
     * (agenda del médico por ubicación).
     */
    public List<LocalDateTime> obtenerHuecosLibres(Long medicoId, String fecha, String centroCodigo) {
        LocalDate fechaDate = LocalDate.parse(fecha);  // espera formato "YYYY-MM-DD"
        LocalDateTime inicioDia = fechaDate.atStartOfDay();
        // 23:59:59 (sin nanos): LocalTime.MAX (…999999999) lo redondea PostgreSQL a
        // 00:00:00 del día siguiente y colaría el primer hueco del día siguiente.
        LocalDateTime finDia = fechaDate.atTime(23, 59, 59);

        // Obtener todos los horarios del médico para ese día (rango de horas)
        List<Horario> horariosDia = horarioRepository.findByMedicoIdAndInicioBetween(medicoId, inicioDia, finDia);
        if (horariosDia.isEmpty()) {
            return List.of();  // no trabaja ese día
        }

        // Obtener las horas ya ocupadas (citas confirmadas para ese médico y día)
        List<LocalDateTime> ocupadas = citaRepository.findHorasOcupadas(medicoId, inicioDia, finDia);

        final String centro = (centroCodigo == null || centroCodigo.isBlank())
                ? null : centroCodigo.trim().toLowerCase();

        // Filtrar: disponibles, no ocupados y (si se pidió) del centro indicado
        return horariosDia.stream()
                .filter(Horario::isDisponible)
                .filter(h -> centro == null
                        || (h.getCentro() != null && centro.equals(h.getCentro().getCodigo())))
                .map(Horario::getInicio)
                .filter(inicio -> !ocupadas.contains(inicio))
                .sorted()
                .collect(Collectors.toList());
    }
}