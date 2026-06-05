package com.saludybienestar.agramonte.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Habilita la auditoría JPA ({@code @CreatedDate} / {@code @LastModifiedDate}).
 *
 * <p>Se mantiene aparte de la clase de arranque para que los <i>slices</i> de prueba
 * web ({@code @WebMvcTest}) no intenten inicializar el handler de auditoría sin un
 * metamodelo JPA disponible.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
