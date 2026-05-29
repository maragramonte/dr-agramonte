package com.saludybienestar.agramonte.repository;

import com.saludybienestar.agramonte.entity.Horario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HorarioRepository extends JpaRepository<Horario, Long> {

    List<Horario> findByMedicoIdAndDisponibleTrue(Long medicoId);

    List<Horario> findByMedicoIdAndInicioBetween(Long medicoId, LocalDateTime start, LocalDateTime end);
    Optional<Horario> findByMedicoIdAndInicio(Long medicoId, LocalDateTime inicio);

    // Bloqueo pesimista para la reserva
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Horario h WHERE h.medico.id = :medicoId AND h.inicio = :fechaHora AND h.disponible = true")
    Optional<Horario> findByMedicoIdAndInicioAndDisponibleTrueForUpdate(
            @Param("medicoId") Long medicoId,
            @Param("fechaHora") LocalDateTime fechaHora
    );
}