package com.saludybienestar.agramonte.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nombre;

    private String telefono;

    @Enumerated(EnumType.STRING)
    private Rol rol = Rol.PACIENTE;

    private boolean enabled = true;

    /** Cuenta creada al reservar sin registro previo; permite nuevas reservas sin login hasta fijar contraseña. */
    @Column(nullable = false)
    private boolean cuentaInvitada = false;

    /** Chat de Telegram vinculado; si es null el paciente no recibe avisos por ese canal. */
    @Column(name = "telegram_chat_id", unique = true)
    private String telegramChatId;

    /** Token de un solo uso que el paciente envía como «/start &lt;token&gt;» para vincular su chat. */
    @Column(name = "telegram_link_token", unique = true)
    private String telegramLinkToken;

    /** Caducidad del token de vinculación; pasada esa fecha el webhook lo rechaza. */
    @Column(name = "telegram_link_token_expira_en")
    private LocalDateTime telegramLinkTokenExpiraEn;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // Igualdad por identidad (id): segura para JPA y para usar la entidad en colecciones.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Usuario other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}