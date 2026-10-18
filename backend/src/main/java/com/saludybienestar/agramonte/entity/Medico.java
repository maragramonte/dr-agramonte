package com.saludybienestar.agramonte.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "medicos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Medico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    private String especialidad;

    private String email;

    /**
     * Cuenta con la que entra este médico. Es lo que permite deducir del JWT de quién
     * es la agenda, en vez de aceptar un {@code ?medicoId=} que cualquier médico podía
     * cambiar para leer la de otro.
     *
     * <p>Nullable: una ficha puede existir sin cuenta (los datos de demostración lo son).
     * El {@code UNIQUE} de la columna (V12) impide que una misma cuenta gobierne dos
     * agendas. El correo de la ficha no sirve como vínculo en caliente:
     * {@code MedicoCuentaInicializador} puede renombrar el de la cuenta sin tocar el suyo.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", unique = true)
    private Usuario usuario;

    @OneToMany(mappedBy = "medico", cascade = CascadeType.ALL)
    private Set<Horario> horarios = new HashSet<>();

    @OneToMany(mappedBy = "medico")
    private Set<Cita> citas = new HashSet<>();

    // Igualdad por identidad (id): evita recursión por las relaciones y es segura en colecciones.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Medico other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
