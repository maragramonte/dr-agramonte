package com.saludybienestar.agramonte.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReservaPublicaRequest {

    @NotBlank
    @Size(max = 255)
    private String nombre;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(max = 20)
    private String telefono;

    @Size(min = 6, max = 100)
    private String password;

    @NotNull
    private Long medicoId;

    @NotNull
    private LocalDateTime fechaHora;

    @Size(max = 500)
    private String motivo;
}
