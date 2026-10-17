package com.saludybienestar.agramonte.controller;

import com.saludybienestar.agramonte.dto.request.CrearCitaRequest;
import com.saludybienestar.agramonte.dto.request.ReservaPublicaRequest;
import com.saludybienestar.agramonte.dto.response.CitaResponse;
import com.saludybienestar.agramonte.dto.response.ReservaPublicaResponse;
import com.saludybienestar.agramonte.dto.response.PacienteAgendaResponse;
import com.saludybienestar.agramonte.dto.response.ReservaPruebaResponse;
import com.saludybienestar.agramonte.security.CustomUserDetails;
import com.saludybienestar.agramonte.service.CitaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/citas")
@RequiredArgsConstructor
public class CitaController {

    /** Cabecera que transporta el token de acceso de una cita de invitado. */
    public static final String CABECERA_TOKEN = "X-Cita-Token";

    private final CitaService citaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CitaResponse crearCita(@AuthenticationPrincipal CustomUserDetails user,
                                  @Valid @RequestBody CrearCitaRequest request) {
        return citaService.crearCita(user.getId(), request);
    }

    /** Reserva sin JWT: crea o reutiliza cuenta de invitado y persiste la cita en BD. */
    @PostMapping("/reserva-publica")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaPublicaResponse reservaPublica(@Valid @RequestBody ReservaPublicaRequest request) {
        return citaService.reservaPublica(request);
    }

    @GetMapping("/mias")
    public List<CitaResponse> misCitas(@AuthenticationPrincipal CustomUserDetails user) {
        return citaService.findByPaciente(user.getId());
    }

    /**
     * Consulta de una cita reservada sin cuenta. La credencial es el token de acceso que
     * devolvió la reserva, no el email: un correo lo puede conocer cualquiera, así que
     * identificar al paciente por él dejaba su historial (y el motivo de consulta, que es
     * dato de salud) a la vista de quien lo supiera.
     *
     * <p>El token viaja en la cabecera {@code X-Cita-Token} y no en la URL: las rutas
     * acaban en los logs del servidor, en el historial del navegador y en la cabecera
     * {@code Referer}, y ahí no debe quedar una credencial. Se declara opcional para que
     * un token ausente y uno equivocado reciban la misma respuesta (404) en lugar de
     * distinguirse con un 400.
     */
    @GetMapping("/publica/{id}")
    public CitaResponse citaPublica(@PathVariable Long id,
                                    @RequestHeader(name = CABECERA_TOKEN, required = false) String token) {
        return citaService.buscarCitaPublica(id, token);
    }

    /** Cancelación de una cita de invitado; exige el token de acceso de esa misma cita. */
    @DeleteMapping("/publica/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarCitaPublica(@PathVariable Long id,
                                    @RequestHeader(name = CABECERA_TOKEN, required = false) String token) {
        citaService.cancelarCitaPublica(id, token);
    }

    /**
     * Lista pacientes con citas del médico (panel médico). Devuelve datos personales
     * (nombre, email, teléfono y citas), por lo que exige JWT con rol MEDICO o ADMIN:
     * lo aplica la regla {@code /api/citas/agenda/**} de {@code SecurityConfig}.
     */
    @GetMapping("/agenda/pacientes")
    public List<PacienteAgendaResponse> pacientesAgenda(@RequestParam(defaultValue = "1") Long medicoId) {
        return citaService.listarPacientesAgenda(medicoId);
    }

    /**
     * Historial de reservas del médico (vista de seguimiento, no es el panel clínico).
     * Contiene datos identificativos del paciente, así que exige JWT con rol MEDICO o ADMIN
     * por la regla {@code /api/citas/agenda/**} de {@code SecurityConfig}.
     */
    @GetMapping("/agenda/reservas")
    public List<ReservaPruebaResponse> reservasPrueba(
            @RequestParam(defaultValue = "1") Long medicoId,
            @RequestParam(required = false) String centroCodigo) {
        return citaService.listarReservasPrueba(medicoId, centroCodigo);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarCita(@AuthenticationPrincipal CustomUserDetails user,
                             @PathVariable Long id) {
        citaService.cancelarCita(user.getId(), id);
    }
}