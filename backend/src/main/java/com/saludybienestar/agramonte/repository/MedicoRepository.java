package com.saludybienestar.agramonte.repository;

import com.saludybienestar.agramonte.entity.Medico;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicoRepository extends JpaRepository<Medico, Long> {

    List<Medico> findByEspecialidad(String especialidad);
}
