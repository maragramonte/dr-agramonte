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

    /** Citas del paciente por email (invitado sin JWT). Solo para prototipo TFG. */
    @GetMapping("/por-email")
    public List<CitaResponse> citasPorEmail(@RequestParam String email) {
        return citaService.findByPacienteEmail(email);
    }

    @DeleteMapping("/publica/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarCitaPublica(@PathVariable Long id, @RequestParam String email) {
        citaService.cancelarCitaPublica(email, id);
    }

    /**
     * Lista pacientes con citas del médico (panel médico). Público en lectura para el prototipo del TFG;
     * en producción conviene exigir rol MEDICO/ADMIN.
     */
    @GetMapping("/agenda/pacientes")
    public List<PacienteAgendaResponse> pacientesAgenda(@RequestParam(defaultValue = "1") Long medicoId) {
        return citaService.listarPacientesAgenda(medicoId);
    }

    /** Historial de reservas para pruebas / demo TFG (no es el panel clínico en producción). */
    @GetMapping("/agenda/reservas")
    public List<ReservaPruebaResponse> reservasPrueba(@RequestParam(defaultValue = "1") Long medicoId) {
        return citaService.listarReservasPrueba(medicoId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarCita(@AuthenticationPrincipal CustomUserDetails user,
                             @PathVariable Long id) {
        citaService.cancelarCita(user.getId(), id);
    }
}