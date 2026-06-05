package com.saludybienestar.agramonte.dto.response;

import com.saludybienestar.agramonte.entity.EstadoCita;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReservaPruebaResponse {
    private Long citaId;
    private Long usuarioId;
    private String pacienteNombre;
    private String pacienteEmail;
    private String pacienteTelefono;
    private Long medicoId;
    private String medicoNombre;
    private LocalDateTime fechaHora;
    private String motivo;
    private EstadoCita estado;
    private String centroCodigo;
    private String centroNombre;
}
