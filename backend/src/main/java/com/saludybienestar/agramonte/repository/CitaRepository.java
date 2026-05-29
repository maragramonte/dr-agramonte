package com.saludybienestar.agramonte.repository;

import com.saludybienestar.agramonte.entity.Cita;
import com.saludybienestar.agramonte.entity.EstadoCita;
import com.saludybienestar.agramonte.entity.Usuario;
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
}
