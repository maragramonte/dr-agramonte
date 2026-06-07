package com.saludybienestar.agramonte.repository.projection;

/** Proyección para la carga de trabajo: citas agrupadas por médico. */
public interface ConteoPorMedico {
    String getNombre();
    String getEspecialidad();
    long getTotal();
}
