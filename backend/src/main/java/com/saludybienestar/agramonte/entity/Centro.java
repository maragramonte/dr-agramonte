package com.saludybienestar.agramonte.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Centro o consulta donde el médico atiende (multi-ubicación).
 * El {@code codigo} ('madrid', 'palma', ...) coincide con el identificador
 * que el frontend ya usaba para seleccionar centro, lo que mantiene la
 * compatibilidad sin tener que reescribir la web.
 */
@Entity
@Table(name = "centros")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Centro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    private String direccion;

    private String ciudad;

    // Igualdad por identidad (id): segura para JPA y colecciones.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Centro other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
