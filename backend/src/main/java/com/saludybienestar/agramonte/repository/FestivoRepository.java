package com.saludybienestar.agramonte.repository;

import com.saludybienestar.agramonte.entity.Festivo;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FestivoRepository extends JpaRepository<Festivo, LocalDate> {

    /** Solo las fechas: el generador de agenda las compara en memoria. */
    @Query("SELECT f.fecha FROM Festivo f WHERE f.fecha BETWEEN :desde AND :hasta")
    List<LocalDate> findFechas(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
