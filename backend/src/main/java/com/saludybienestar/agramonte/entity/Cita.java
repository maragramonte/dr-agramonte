package com.saludybienestar.agramonte.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "citas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    /** Centro donde se atiende la cita. Nullable: las citas antiguas no lo tienen. */
    @ManyToOne
    @JoinColumn(name = "centro_id")
    private Centro centro;

    private LocalDateTime fechaHora;

    private String motivo;

    @Enumerated(EnumType.STRING)
    private EstadoCita estado = EstadoCita.PENDIENTE;

    /** Evita reenviar el recordatorio Twilio 24 h antes. */
    private boolean recordatorioEnviado = false;

    /** Cobertura elegida en la reserva: 'privada' o 'seguro'. */
    private String cobertura;

    /** Aseguradora si la cobertura es seguro (Sanitas, Adeslas...). Nullable. */
    private String aseguradora;

    /** Nº de tarjeta sanitaria / póliza si paga por seguro. Nullable. */
    private String numeroTarjetaSanitaria;

    /** Preferencia de pago si es privada: 'en-consulta' u 'online'. Nullable. */
    private String preferenciaPago;

    /**
     * SHA-256 en hexadecimal del token de acceso entregado al reservar sin cuenta.
     * Es la credencial con la que un invitado consulta y cancela SU cita: antes se
     * usaba el email, que cualquiera puede conocer.
     *
     * <p>No se guarda el token en claro, y se compara por hash, así que no puede ser
     * BCrypt: su sal aleatoria no es reproducible. Como el token son 32 bytes de
     * {@code SecureRandom} y no una contraseña elegida por una persona, un SHA-256
     * sin sal no es atacable por diccionario ni por tablas precalculadas.
     *
     * <p>Nullable: las citas creadas con sesión se identifican con el JWT del paciente.
     */
    @Column(name = "acceso_token_hash", length = 64)
    private String accesoTokenHash;

    // Igualdad por identidad (id): segura para JPA y para usar la entidad en colecciones.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cita other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
