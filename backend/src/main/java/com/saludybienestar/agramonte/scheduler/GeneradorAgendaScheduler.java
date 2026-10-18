package com.saludybienestar.agramonte.scheduler;

import com.saludybienestar.agramonte.config.AgendaProperties;
import com.saludybienestar.agramonte.entity.Centro;
import com.saludybienestar.agramonte.entity.Horario;
import com.saludybienestar.agramonte.entity.Medico;
import com.saludybienestar.agramonte.repository.CentroRepository;
import com.saludybienestar.agramonte.repository.HorarioRepository;
import com.saludybienestar.agramonte.repository.MedicoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantiene la agenda con huecos libres hasta {@code app.agenda.dias-futuro} por delante.
 *
 * <p>Antes las franjas solo las sembraban las migraciones V3 y V6, que generaban unos
 * 90 días contados desde el primer arranque. Pero una migración de Flyway se ejecuta
 * una única vez: pasada esa ventana la agenda se quedaba vacía, el calendario de
 * {@code reservar.html} no ofrecía ni un hueco y nadie podía pedir cita, sin ningún
 * error que lo delatara. Esto lo arregla generando cada día el tramo que falta al
 * final de la ventana.
 *
 * <p>Es idempotente: solo inserta las franjas cuya fecha y hora no existen ya para ese
 * médico (hay un índice único por {@code medico_id, inicio}). Por eso no resucita un
 * hueco ya reservado ni vuelve a marcar como libre uno ocupado. La contrapartida es
 * que un hueco borrado a mano dentro de la ventana se vuelve a crear; para cerrar un
 * día hay que quitarlo de {@code app.agenda.centro-por-dia} o marcarlo no disponible.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeneradorAgendaScheduler {

    private final AgendaProperties agenda;
    private final MedicoRepository medicoRepository;
    private final CentroRepository centroRepository;
    private final HorarioRepository horarioRepository;

    /**
     * También al arrancar: si la aplicación ha estado parada más que la ventana (o es
     * el primer despliegue tras añadir esto), la agenda debe llenarse sin esperar al
     * cron de la madrugada.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void generarAlArrancar() {
        // La anotación va también aquí a propósito: esta llamada es interna, no pasa por
        // el proxy de Spring, así que el @Transactional del método de abajo no se aplica
        // por sí solo. Con la transacción abierta desde aquí, sí queda cubierta.
        generarHuecosFuturos();
    }

    @Scheduled(cron = "${app.agenda.cron:0 30 3 * * *}")
    @Transactional
    public void generarHuecosFuturos() {
        List<LocalTime> franjas = agenda.franjasDelDia();
        if (franjas.isEmpty() || agenda.getDiasFuturo() <= 0) {
            log.warn("Generación de agenda desactivada por configuración ({} franjas, {} días).",
                    franjas.size(), agenda.getDiasFuturo());
            return;
        }

        List<Medico> medicos = medicoRepository.findAll();
        if (medicos.isEmpty()) {
            log.warn("No hay médicos: no se genera agenda.");
            return;
        }

        Map<String, Centro> centrosPorCodigo = new HashMap<>();
        for (Centro centro : centroRepository.findAll()) {
            centrosPorCodigo.put(centro.getCodigo(), centro);
        }

        // La ventana arranca mañana: el día en curso no se rellena, porque sus horas ya
        // pasadas no son reservables y las migraciones tampoco lo hacían.
        LocalDate desde = LocalDate.now().plusDays(1);
        LocalDate hasta = LocalDate.now().plusDays(agenda.getDiasFuturo());
        LocalDateTime ventanaInicio = desde.atStartOfDay();
        LocalDateTime ventanaFin = hasta.atTime(LocalTime.MAX);

        int creados = 0;
        for (Medico medico : medicos) {
            creados += generarParaMedico(
                    medico, franjas, centrosPorCodigo, desde, hasta, ventanaInicio, ventanaFin);
        }

        if (creados > 0) {
            log.info("Agenda ampliada: {} franja(s) nueva(s) hasta {}.", creados, hasta);
        } else {
            log.debug("Agenda ya cubierta hasta {}: nada que generar.", hasta);
        }
    }

    private int generarParaMedico(Medico medico,
                                  List<LocalTime> franjas,
                                  Map<String, Centro> centrosPorCodigo,
                                  LocalDate desde,
                                  LocalDate hasta,
                                  LocalDateTime ventanaInicio,
                                  LocalDateTime ventanaFin) {
        // Una sola consulta por médico con lo que ya existe en la ventana; comparar en
        // memoria evita un SELECT por franja (serían ~1.300 en 90 días).
        Set<LocalDateTime> existentes = new HashSet<>(
                horarioRepository.findInicios(medico.getId(), ventanaInicio, ventanaFin));

        List<Horario> nuevos = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            String codigoCentro = agenda.getCentroPorDia().get(dia.getDayOfWeek());
            if (codigoCentro == null) {
                continue; // día cerrado (por defecto, el domingo)
            }
            Centro centro = centrosPorCodigo.get(codigoCentro);
            if (centro == null) {
                log.warn("El día {} apunta al centro '{}', que no existe en la tabla centros.",
                        dia.getDayOfWeek(), codigoCentro);
                continue;
            }

            for (LocalTime franja : franjas) {
                LocalDateTime inicio = dia.atTime(franja);
                if (existentes.contains(inicio)) {
                    continue;
                }
                Horario horario = new Horario();
                horario.setMedico(medico);
                horario.setCentro(centro);
                horario.setInicio(inicio);
                horario.setFin(inicio.plusMinutes(agenda.getMinutosPorFranja()));
                horario.setDisponible(true);
                nuevos.add(horario);
            }
        }

        if (nuevos.isEmpty()) {
            return 0;
        }
        horarioRepository.saveAll(nuevos);
        return nuevos.size();
    }
}
