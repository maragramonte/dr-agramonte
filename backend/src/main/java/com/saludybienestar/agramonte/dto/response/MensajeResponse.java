package com.saludybienestar.agramonte.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Respuesta de una sola frase, para los casos en los que lo correcto es no contar nada
 * más: por ejemplo cuando se ha enviado un correo de activación y la respuesta no debe
 * revelar en qué estado está la cuenta.
 */
@Data
@AllArgsConstructor
public class MensajeResponse {

    private String message;
}
