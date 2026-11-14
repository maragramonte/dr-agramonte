package com.saludybienestar.agramonte.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Día en que la consulta cierra aunque caiga entre semana: festivos nacionales,
 * autonómicos de Illes Balears y locales de Palma. La lista la siembra V16; cada año
 * nuevo necesita su propia migración.
 */
@Entity
@Table(name = "festivos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Festivo {

    @Id
    private LocalDate fecha;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Festivo other)) return false;
        return fecha != null && fecha.equals(other.fecha);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
