package com.saludybienestar.agramonte.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos agregados para el cuadro de mando de gestión (módulo SGE).
 * Entiende la clínica como una empresa: carga por médico, demanda por
 * especialidad, reparto por centro, evolución temporal y tasa de cancelación.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstadisticasResponse {

    /** KPIs principales (tarjetas superiores del dashboard). */
    private long totalCitas;
    private long activas;        // PENDIENTE + CONFIRMADA
    private long completadas;
    private long canceladas;
    private double tasaCancelacion; // porcentaje 0–100, redondeado a 1 decimal

    /** Series para los gráficos. */
    private List<ItemConteo> porMedico;
    private List<ItemConteo> porEspecialidad;
    private List<ItemConteo> porCentro;
    private List<ItemConteo> porEstado;
    private List<ItemConteo> porMes;

    /** Par etiqueta/valor reutilizable por todos los gráficos. {@code detalle} es opcional. */
    @Data
    @AllArgsConstructor
    public static class ItemConteo {
        private String etiqueta;
        private String detalle;
        private long total;
    }
}
