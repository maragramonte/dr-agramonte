package com.saludybienestar.agramonte.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CrearCitaRequest {
    @NotNull
    private Long medicoId;

    @NotNull
    private LocalDateTime fechaHora;

    @Size(max = 500)
    private String motivo;

    /** Código del centro donde se atiende ('madrid', 'palma'...). Opcional. */
    @Size(max = 50)
    private String centroCodigo;

    /** Actualiza el teléfono del paciente para avisos Twilio (formato +34...). */
    @Size(max = 20)
    private String telefono;

    /** Cobertura: 'privada' o 'seguro'. */
    @Size(max = 20)
    private String cobertura;

    /** Aseguradora si paga por seguro. */
    @Size(max = 100)
    private String aseguradora;

    /** Nº de tarjeta sanitaria / póliza si paga por seguro. */
    @Size(max = 100)
    private String numeroTarjetaSanitaria;

    /** Preferencia de pago si es privada: 'en-consulta' u 'online'. */
    @Size(max = 20)
    private String preferenciaPago;
}