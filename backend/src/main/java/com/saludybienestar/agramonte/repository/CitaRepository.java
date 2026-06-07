package com.saludybienestar.agramonte.repository;

import com.saludybienestar.agramonte.entity.Cita;
import com.saludybienestar.agramonte.entity.EstadoCita;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.projection.ConteoPorClave;
import com.saludybienestar.agramonte.repository.projection.ConteoPorEstado;
import com.saludybienestar.agramonte.repository.projection.ConteoPorMedico;
import com.saludybienestar.agramonte.repository.projection.ConteoPorMes;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CitaRepository extends JpaRepository<Cita, Long> {

    List<Cita> findByUsuario(Usuario usuario);
    List<Cita> findByUsuarioId(Long usuarioId);

    @Query("""
            SELECT c FROM Cita c
            JOIN FETCH c.usuario
            JOIN FETCH c.medico
            WHERE LOWER(c.usuario.email) = LOWER(:email)
            ORDER BY c.fechaHora DESC
            """)
    List<Cita> findByUsuarioEmailWithMedico(@Param("email") String email);

    List<Cita> findByMedicoId(Long medicoId);

    @Query("""
            SELECT c FROM Cita c
            JOIN FETCH c.usuario
            JOIN FETCH c.medico
            WHERE c.medico.id = :medicoId
              AND c.estado <> com.saludybienestar.agramonte.entity.EstadoCita.CANCELADA
            ORDER BY c.fechaHora DESC
            """)
    List<Cita> findActivasByMedicoIdWithPaciente(@Param("medicoId") Long medicoId);

    @Query("""
            SELECT c FROM Cita c
            JOIN FETCH c.usuario
            JOIN FETCH c.medico
            WHERE c.medico.id = :medicoId
            ORDER BY c.fechaHora DESC
            """)
    List<Cita> findAllByMedicoIdWithPaciente(@Param("medicoId") Long medicoId);

    boolean existsByMedicoIdAndFechaHora(Long medicoId, LocalDateTime fechaHora);

    @Query("SELECT c.fechaHora FROM Cita c WHERE c.medico.id = :medicoId AND c.fechaHora BETWEEN :inicio AND :fin AND c.estado != 'CANCELADA'")
    List<LocalDateTime> findHorasOcupadas(@Param("medicoId") Long medicoId, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query("""
            SELECT c FROM Cita c
            JOIN FETCH c.usuario
            JOIN FETCH c.medico
            WHERE c.estado = :estado
              AND c.recordatorioEnviado = false
              AND c.fechaHora BETWEEN :desde AND :hasta
            """)
    List<Cita> findPendientesDeRecordatorio(
            @Param("estado") EstadoCita estado,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    // ── Estadísticas / cuadro de mando (módulo SGE) ────────────────────────────
    // Agregaciones GROUP BY para el dashboard de gestión. Devuelven proyecciones
    // ligeras (no entidades) para no traer toda la cita a memoria.

    /** Reparto de citas por estado: de aquí se derivan total, activas y tasa de cancelación. */
    @Query("""
            SELECT c.estado AS estado, COUNT(c) AS total
            FROM Cita c
            GROUP BY c.estado
            """)
    List<ConteoPorEstado> contarPorEstado();

    /** Carga de trabajo: número de citas que gestiona cada médico, de mayor a menor. */
    @Query("""
            SELECT m.nombre AS nombre, m.especialidad AS especialidad, COUNT(c) AS total
            FROM Cita c JOIN c.medico m
            GROUP BY m.id, m.nombre, m.especialidad
            ORDER BY COUNT(c) DESC
            """)
    List<ConteoPorMedico> contarPorMedico();

    /** Demanda por especialidad médica. */
    @Query("""
            SELECT COALESCE(m.especialidad, 'Sin especialidad') AS clave, COUNT(c) AS total
            FROM Cita c JOIN c.medico m
            GROUP BY m.especialidad
            ORDER BY COUNT(c) DESC
            """)
    List<ConteoPorClave> contarPorEspecialidad();

    /** Distribución de citas por centro/consulta (LEFT JOIN: incluye citas sin centro). */
    @Query("""
            SELECT COALESCE(ce.nombre, 'Sin centro') AS clave, COUNT(c) AS total
            FROM Cita c LEFT JOIN c.centro ce
            GROUP BY ce.nombre
            ORDER BY COUNT(c) DESC
            """)
    List<ConteoPorClave> contarPorCentro();

    /** Tendencia temporal: citas creadas por año y mes (orden cronológico). */
    @Query("""
            SELECT YEAR(c.fechaHora) AS anio, MONTH(c.fechaHora) AS mes, COUNT(c) AS total
            FROM Cita c
            GROUP BY YEAR(c.fechaHora), MONTH(c.fechaHora)
            ORDER BY YEAR(c.fechaHora), MONTH(c.fechaHora)
            """)
    List<ConteoPorMes> contarPorMes();
}
