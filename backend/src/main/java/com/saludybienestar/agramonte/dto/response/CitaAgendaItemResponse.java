package com.saludybienestar.agramonte.dto.response;

import com.saludybienestar.agramonte.entity.EstadoCita;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CitaAgendaItemResponse {
    private Long id;
    private LocalDateTime fechaHora;
    private String motivo;
    private EstadoCita estado;
}
