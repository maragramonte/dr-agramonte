package com.saludybienestar.agramonte.repository.projection;

import com.saludybienestar.agramonte.entity.EstadoCita;

/** Proyección para el reparto de citas por estado (confirmadas, canceladas, ...). */
public interface ConteoPorEstado {
    EstadoCita getEstado();
    long getTotal();
}
