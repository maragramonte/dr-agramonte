package com.saludybienestar.agramonte.repository.projection;

/** Proyección para la tendencia temporal: citas agrupadas por año y mes. */
public interface ConteoPorMes {
    int getAnio();
    int getMes();
    long getTotal();
}
