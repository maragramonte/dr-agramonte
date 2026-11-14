package com.saludybienestar.agramonte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.saludybienestar.agramonte.config.AgendaProperties;
import com.saludybienestar.agramonte.repository.FestivoRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalendarioLaboralTest {

    @Mock private FestivoRepository festivoRepository;

    private CalendarioLaboral calendario;

    private static final LocalDate LUNES =
            LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach
    void setUp() {
        calendario = new CalendarioLaboral(new AgendaProperties(), festivoRepository);
    }

    @Test
    @DisplayName("De lunes a viernes abre, salvo que sea festivo")
    void laborables_abren() {
        for (int i = 0; i < 5; i++) {
            LocalDate dia = LUNES.plusDays(i);
            when(festivoRepository.existsById(dia)).thenReturn(false);
            assertThat(calendario.estaCerrado(dia)).as(dia.getDayOfWeek().toString()).isFalse();
        }
    }

    @Test
    @DisplayName("Sábado y domingo cierran sin consultar la tabla de festivos")
    void finDeSemana_cierra() {
        assertThat(calendario.estaCerrado(LUNES.plusDays(5))).isTrue();
        assertThat(calendario.estaCerrado(LUNES.plusDays(6))).isTrue();
    }

    @Test
    @DisplayName("Un festivo entre semana cierra")
    void festivo_cierra() {
        LocalDate miercoles = LUNES.plusDays(2);
        when(festivoRepository.existsById(miercoles)).thenReturn(true);
        assertThat(calendario.estaCerrado(miercoles)).isTrue();
    }
}
