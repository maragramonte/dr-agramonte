package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.dto.response.EstadisticasResponse;
import com.saludybienestar.agramonte.dto.response.EstadisticasResponse.ItemConteo;
import com.saludybienestar.agramonte.entity.EstadoCita;
import com.saludybienestar.agramonte.repository.CitaRepository;
import com.saludybienestar.agramonte.repository.projection.ConteoPorEstado;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genera el cuadro de mando de gestión a partir de agregaciones SQL.
 * Solo lectura: no modifica datos, por eso {@code readOnly = true}.
 */
@Service
@RequiredArgsConstructor
public class EstadisticaService {

    private static final Locale ES = Locale.forLanguageTag("es-ES");

    private final CitaRepository citaRepository;

    @Transactional(readOnly = true)
    public EstadisticasResponse generar() {
        Map<EstadoCita, Long> porEstado = new EnumMap<>(EstadoCita.class);
        for (ConteoPorEstado fila : citaRepository.contarPorEstado()) {
            porEstado.put(fila.getEstado(), fila.getTotal());
        }

        long total = porEstado.values().stream().mapToLong(Long::longValue).sum();
        long canceladas = porEstado.getOrDefault(EstadoCita.CANCELADA, 0L);
        long completadas = porEstado.getOrDefault(EstadoCita.COMPLETADA, 0L);
        long activas = porEstado.getOrDefault(EstadoCita.PENDIENTE, 0L)
                + porEstado.getOrDefault(EstadoCita.CONFIRMADA, 0L);
        double tasaCancelacion = total == 0 ? 0.0 : Math.round((canceladas * 1000.0) / total) / 10.0;

        List<ItemConteo> medicos = citaRepository.contarPorMedico().stream()
                .map(m -> new ItemConteo(m.getNombre(), m.getEspecialidad(), m.getTotal()))
                .toList();

        List<ItemConteo> especialidades = citaRepository.contarPorEspecialidad().stream()
                .map(c -> new ItemConteo(c.getClave(), null, c.getTotal()))
                .toList();

        List<ItemConteo> centros = citaRepository.contarPorCentro().stream()
                .map(c -> new ItemConteo(c.getClave(), null, c.getTotal()))
                .toList();

        List<ItemConteo> estados = porEstado.entrySet().stream()
                .map(e -> new ItemConteo(estadoLabel(e.getKey()), null, e.getValue()))
                .toList();

        List<ItemConteo> meses = citaRepository.contarPorMes().stream()
                .map(m -> new ItemConteo(etiquetaMes(m.getAnio(), m.getMes()), null, m.getTotal()))
                .toList();

        return new EstadisticasResponse(
                total, activas, completadas, canceladas, tasaCancelacion,
                medicos, especialidades, centros, estados, meses);
    }

    private String estadoLabel(EstadoCita estado) {
        return switch (estado) {
            case PENDIENTE -> "Pendiente";
            case CONFIRMADA -> "Confirmada";
            case CANCELADA -> "Cancelada";
            case COMPLETADA -> "Completada";
        };
    }

    /** "2026-06" → "jun. 2026" para el eje del gráfico de tendencia. */
    private String etiquetaMes(int anio, int mes) {
        String nombre = Month.of(mes).getDisplayName(TextStyle.SHORT, ES);
        return nombre + " " + anio;
    }
}
