package com.saludybienestar.agramonte.config;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Patrón semanal con el que se generan los huecos de la agenda.
 *
 * <p>Los valores por defecto reproducen exactamente lo que sembraron las migraciones
 * V3 y V6, que es la agenda real de la consulta: franjas de 30 minutos de 9:00 a
 * 18:30, sin las horas 14 y 15 (comida), y un centro por día de la semana. Están
 * aquí y no en un .sql porque una migración se ejecuta una sola vez: las franjas
 * hay que seguir creándolas cada día que pasa (ver
 * {@link com.saludybienestar.agramonte.scheduler.GeneradorAgendaScheduler}).
 */
@Data
@ConfigurationProperties(prefix = "app.agenda")
public class AgendaProperties {

    /** Mantener huecos creados hasta tantos días por delante de hoy. */
    private int diasFuturo = 90;

    /** Primera franja del día. */
    private LocalTime horaInicio = LocalTime.of(9, 0);

    /** Comienzo de la última franja del día (la de 18:30 acaba a las 19:00). */
    private LocalTime horaFin = LocalTime.of(18, 30);

    /** Duración de cada franja, en minutos. */
    private int minutosPorFranja = 30;

    /** Horas completas sin consulta (14 y 15 = de 14:00 a 15:59). */
    private List<Integer> horasExcluidas = new ArrayList<>(List.of(14, 15));

    /**
     * Centro que atiende cada día de la semana. Un día que no aparezca no genera
     * huecos: así el domingo queda cerrado sin necesidad de una regla aparte.
     */
    private Map<DayOfWeek, String> centroPorDia = new LinkedHashMap<>(Map.of(
            DayOfWeek.MONDAY, "madrid",
            DayOfWeek.TUESDAY, "madrid",
            DayOfWeek.WEDNESDAY, "palma",
            DayOfWeek.THURSDAY, "madrid",
            DayOfWeek.FRIDAY, "palma",
            DayOfWeek.SATURDAY, "palma"
    ));

    /** Cron Spring de la generación; por defecto cada día a las 03:30. */
    private String cron = "0 30 3 * * *";

    /** Franjas de un día de consulta, ya descontadas las horas excluidas. */
    public List<LocalTime> franjasDelDia() {
        List<LocalTime> franjas = new ArrayList<>();
        if (minutosPorFranja <= 0 || horaInicio.isAfter(horaFin)) {
            return franjas;
        }
        // Se cuenta el número de pasos en vez de comparar LocalTime en el bucle:
        // plusMinutes() da la vuelta a medianoche y una configuración rara dejaría
        // la condición de salida sin cumplirse nunca.
        int pasos = (int) ((horaFin.toSecondOfDay() - horaInicio.toSecondOfDay()) / 60L / minutosPorFranja);
        for (int i = 0; i <= pasos; i++) {
            LocalTime hora = horaInicio.plusMinutes((long) i * minutosPorFranja);
            if (!horasExcluidas.contains(hora.getHour())) {
                franjas.add(hora);
            }
        }
        return franjas;
    }
}
