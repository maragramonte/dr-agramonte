package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.config.AgendaProperties;
import com.saludybienestar.agramonte.repository.FestivoRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Responde a una sola pregunta: ¿la consulta abre este día?
 *
 * <p>Cierra los días que no tienen centro en {@code app.agenda.centro-por-dia} (sábado
 * y domingo con la configuración por defecto) y los de la tabla {@code festivos}.
 *
 * <p>El generador ya no crea huecos esos días, pero quedan franjas antiguas: las que
 * tenían cita cuando entró V16 y vuelven a quedar libres si el paciente cancela. Por
 * eso la disponibilidad y la reserva preguntan aquí en vez de fiarse de la tabla.
 */
@Service
@RequiredArgsConstructor
public class CalendarioLaboral {

    private final AgendaProperties agenda;
    private final FestivoRepository festivoRepository;

    public boolean estaCerrado(LocalDate dia) {
        return !agenda.getCentroPorDia().containsKey(dia.getDayOfWeek())
                || festivoRepository.existsById(dia);
    }
}
