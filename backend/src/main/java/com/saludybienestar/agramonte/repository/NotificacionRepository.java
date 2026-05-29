package com.saludybienestar.agramonte.repository;

import com.saludybienestar.agramonte.entity.Notificacion;
import com.saludybienestar.agramonte.entity.Usuario;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findByUsuarioOrderByFechaCreacionDesc(Usuario usuario);
}
