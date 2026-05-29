package com.saludybienestar.agramonte.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservaPublicaResponse {

    private CitaResponse cita;
    /** Presente si el paciente indicó contraseña y quedó autenticado. */
    private AuthResponse sesion;
}
