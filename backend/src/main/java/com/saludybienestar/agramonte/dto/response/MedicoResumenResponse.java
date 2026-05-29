package com.saludybienestar.agramonte.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MedicoResumenResponse {
    private Long id;
    private String nombre;
    private String especialidad;
}
