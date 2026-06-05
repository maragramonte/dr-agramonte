package com.saludybienestar.agramonte.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CentroResponse {
    private Long id;
    private String codigo;
    private String nombre;
    private String direccion;
    private String ciudad;
}
