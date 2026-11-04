package com.saludybienestar.agramonte.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservaPublicaResponse {

    private CitaResponse cita;
    /**
     * Token de acceso a esta cita para quien reserva sin cuenta. Es la única vez que se
     * entrega (en BD solo queda su hash): el cliente debe guardarlo, porque es lo que le
     * permite consultarla y cancelarla sin sesión.
     */
    private String accesoToken;
}
