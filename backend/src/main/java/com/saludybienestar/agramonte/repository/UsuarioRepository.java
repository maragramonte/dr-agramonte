package com.saludybienestar.agramonte.repository;

import com.saludybienestar.agramonte.entity.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Usuario> findByTelegramLinkToken(String telegramLinkToken);

    Optional<Usuario> findByTelegramChatId(String telegramChatId);

    /**
     * Cuenta que espera activación con ese token. Se busca por el hash, nunca por el
     * token en claro: es lo único que hay guardado.
     */
    Optional<Usuario> findByActivacionToken(String activacionTokenHash);
}
