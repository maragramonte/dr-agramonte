package com.saludybienestar.agramonte.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Activación de una cuenta invitada: el token que llegó por correo y la contraseña nueva. */
@Data
public class ActivacionRequest {

    @NotBlank
    @Size(max = 100)
    private String token;

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;
}
