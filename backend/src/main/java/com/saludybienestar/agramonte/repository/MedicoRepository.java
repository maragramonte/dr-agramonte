package com.saludybienestar.agramonte.repository;

import com.saludybienestar.agramonte.entity.Medico;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicoRepository extends JpaRepository<Medico, Long> {

    List<Medico> findByEspecialidad(String especialidad);

    /**
     * Ficha del médico que entra con esa cuenta, si la tiene. Es el paso que convierte
     * un JWT en «de quién es esta agenda», sin fiarse del {@code ?medicoId=} de la URL.
     */
    Optional<Medico> findByUsuarioId(Long usuarioId);
}
