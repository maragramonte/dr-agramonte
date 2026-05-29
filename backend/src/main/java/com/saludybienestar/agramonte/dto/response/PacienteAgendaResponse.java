package com.saludybienestar.agramonte.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PacienteAgendaResponse {
    private Long usuarioId;
    private String nombre;
    private String email;
    private String telefono;
    private List<CitaAgendaItemResponse> citas;
}
