package com.saludybienestar.agramonte.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saludybienestar.agramonte.config.AgendaProperties;
import com.saludybienestar.agramonte.entity.Centro;
import com.saludybienestar.agramonte.entity.Horario;
import com.saludybienestar.agramonte.entity.Medico;
import com.saludybienestar.agramonte.repository.CentroRepository;
import com.saludybienestar.agramonte.repository.HorarioRepository;
import com.saludybienestar.agramonte.repository.MedicoRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pruebas del generador de agenda. Lo que se fija aquí es que la agenda no vuelva a
 * quedarse sin huecos: las migraciones V3 y V6 sembraron unos 90 días contados desde
 * el primer arranque y no se ejecutan nunca más, así que pasada esa ventana el
 * calendario de la reserva se quedaba vacío y nadie podía pedir cita.
 */
@ExtendWith(MockitoExtension.class)
class GeneradorAgendaSchedulerTest {

    @Mock private MedicoRepository medicoRepository;
    @Mock private CentroRepository centroRepository;
    @Mock private HorarioRepository horarioRepository;

    private AgendaProperties agenda;
    private GeneradorAgendaScheduler scheduler;

    private static final Long MEDICO_ID = 1L;

    @BeforeEach
    void setUp() {
        agenda = new AgendaProperties();
        scheduler = new GeneradorAgendaScheduler(
                agenda, medicoRepository, centroRepository, horarioRepository);
    }

    private Medico medico() {
        Medico m = new Medico();
        m.setId(MEDICO_ID);
        m.setNombre("Dr. Agramonte");
        return m;
    }

    private Centro centro(Long id, String codigo) {
        Centro c = new Centro();
        c.setId(id);
        c.setCodigo(codigo);
        c.setNombre("Consulta " + codigo);
        return c;
    }

    private void conAgendaVacia() {
        when(medicoRepository.findAll()).thenReturn(List.of(medico()));
        when(centroRepository.findAll()).thenReturn(List.of(
                centro(1L, "madrid"), centro(2L, "palma")));
        when(horarioRepository.findInicios(anyLong(), any(), any())).thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private List<Horario> capturarGuardados() {
        ArgumentCaptor<List<Horario>> captor = ArgumentCaptor.forClass(List.class);
        verify(horarioRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Con la agenda vacía crea franjas en toda la ventana y ninguna en el pasado")
    void generar_agendaVacia_cubreLaVentana() {
        conAgendaVacia();
        agenda.setDiasFuturo(14);

        scheduler.generarHuecosFuturos();

        List<Horario> creados = capturarGuardados();
        assertThat(creados).isNotEmpty();

        LocalDateTime ahora = LocalDateTime.now();
        LocalDate ultimoDia = LocalDate.now().plusDays(14);
        assertThat(creados).allSatisfy(h -> {
            assertThat(h.getInicio()).isAfter(ahora);
            assertThat(h.getInicio().toLocalDate()).isBeforeOrEqualTo(ultimoDia);
            assertThat(h.isDisponible()).isTrue();
            assertThat(h.getFin()).isEqualTo(h.getInicio().plusMinutes(30));
            assertThat(h.getMedico().getId()).isEqualTo(MEDICO_ID);
        });

        // La ventana de 14 días siempre contiene dos semanas completas, así que la
        // última franja tiene que caer más allá de la primera semana: si solo generara
        // unos pocos días, esto lo detecta.
        assertThat(creados.stream().map(Horario::getInicio).max(LocalDateTime::compareTo).orElseThrow())
                .isAfter(ahora.plusDays(7));
    }

    @Test
    @DisplayName("Respeta el horario de consulta: 9:00–18:30, sin las horas de comida")
    void generar_respetaFranjasDelDia() {
        conAgendaVacia();
        agenda.setDiasFuturo(7);

        scheduler.generarHuecosFuturos();

        Set<LocalTime> horas = capturarGuardados().stream()
                .map(h -> h.getInicio().toLocalTime())
                .collect(Collectors.toSet());

        assertThat(horas).contains(LocalTime.of(9, 0), LocalTime.of(13, 30), LocalTime.of(18, 30));
        assertThat(horas).doesNotContain(
                LocalTime.of(8, 30),   // antes de abrir
                LocalTime.of(14, 0),   // comida
                LocalTime.of(15, 30),  // comida
                LocalTime.of(19, 0));  // después de cerrar
    }

    @Test
    @DisplayName("El domingo no genera nada y cada día va al centro que le toca")
    void generar_asignaCentroPorDiaYCierraElDomingo() {
        conAgendaVacia();
        agenda.setDiasFuturo(21); // tres semanas: entran todos los días de la semana

        scheduler.generarHuecosFuturos();

        List<Horario> creados = capturarGuardados();

        assertThat(creados)
                .as("el domingo la consulta está cerrada")
                .noneSatisfy(h -> assertThat(h.getInicio().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY));

        assertThat(creados).allSatisfy(h -> {
            String esperado = agenda.getCentroPorDia().get(h.getInicio().getDayOfWeek());
            assertThat(h.getCentro().getCodigo()).isEqualTo(esperado);
        });
    }

    @Test
    @DisplayName("No duplica: una franja que ya existe (reservada o libre) no se vuelve a crear")
    void generar_noDuplicaLasFranjasExistentes() {
        when(medicoRepository.findAll()).thenReturn(List.of(medico()));
        when(centroRepository.findAll()).thenReturn(List.of(
                centro(1L, "madrid"), centro(2L, "palma")));
        agenda.setDiasFuturo(7);

        // Se simula que ya está creado todo lo que el generador querría crear.
        List<LocalDateTime> yaExistentes = franjasEsperadas(7);
        when(horarioRepository.findInicios(anyLong(), any(), any())).thenReturn(yaExistentes);

        scheduler.generarHuecosFuturos();

        // Nada que insertar: por eso una franja ya reservada (disponible = false) no
        // vuelve a aparecer como libre.
        verify(horarioRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Sin médicos en la base de datos no intenta crear nada")
    void generar_sinMedicos_noHaceNada() {
        when(medicoRepository.findAll()).thenReturn(List.of());

        scheduler.generarHuecosFuturos();

        verify(horarioRepository, never()).saveAll(any());
    }

    /** Las fechas y horas que el generador debería proponer para una ventana de n días. */
    private List<LocalDateTime> franjasEsperadas(int dias) {
        List<LocalTime> franjas = agenda.franjasDelDia();
        return LocalDate.now().plusDays(1).datesUntil(LocalDate.now().plusDays(dias + 1L))
                .filter(d -> agenda.getCentroPorDia().containsKey(d.getDayOfWeek()))
                .flatMap(d -> franjas.stream().map(d::atTime))
                .toList();
    }
}
