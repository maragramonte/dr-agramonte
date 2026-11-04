package com.saludybienestar.agramonte.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Petición del enlace para elegir una contraseña nueva. */
@Data
public class RecuperarRequest {

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;
}
