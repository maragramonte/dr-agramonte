package com.saludybienestar.agramonte.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TelegramEstadoResponse {
    /** true si el usuario tiene un chat asociado y por tanto recibe avisos. */
    private boolean vinculado;
    /** true si hay un token emitido y aún sin usar ni caducar. */
    private boolean vinculacionPendiente;
    /** Refleja app.telegram.enabled: con el canal apagado no tiene sentido ofrecer la vinculación. */
    private boolean canalActivo;
}
