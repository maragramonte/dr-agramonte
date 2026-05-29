package com.saludybienestar.agramonte.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactoRequest {

    @NotBlank
    @Size(min = 3, max = 120)
    private String nombre;

    @NotBlank
    @Email
    private String email;

    @Size(max = 20)
    private String telefono;

    @Size(max = 80)
    private String motivo;

    @NotBlank
    @Size(min = 10, max = 1000)
    private String mensaje;
}
