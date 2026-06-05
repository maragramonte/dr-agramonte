package com.saludybienestar.agramonte.repository;

import com.saludybienestar.agramonte.entity.Centro;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CentroRepository extends JpaRepository<Centro, Long> {

    Optional<Centro> findByCodigo(String codigo);
}
