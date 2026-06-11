package com.saludybienestar.agramonte.dto.response;

import com.saludybienestar.agramonte.entity.EstadoCita;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class CitaResponse {
    private Long id;
    private Long usuarioId;
    private Long medicoId;
    private String medicoNombre;
    private LocalDateTime fechaHora;
    private String motivo;
    private EstadoCita estado;
    private String centroCodigo;
    private String centroNombre;
    private String cobertura;
    private String aseguradora;
    private String numeroTarjetaSanitaria;
    private String preferenciaPago;
}
