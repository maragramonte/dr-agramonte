package com.saludybienestar.agramonte.controller;

import com.saludybienestar.agramonte.dto.request.CrearCitaRequest;
import com.saludybienestar.agramonte.dto.request.ReservaPublicaRequest;
import com.saludybienestar.agramonte.dto.response.CitaResponse;
import com.saludybienestar.agramonte.dto.response.ReservaPublicaResponse;
import com.saludybienestar.agramonte.dto.response.PacienteAgendaResponse;
import com.saludybienestar.agramonte.dto.response.ReservaPruebaResponse;
import com.saludybienestar.agramonte.entity.Medico;
import com.saludybienestar.agramonte.entity.Rol;
import com.saludybienestar.agramonte.repository.MedicoRepository;
import com.saludybienestar.agramonte.security.CustomUserDetails;
import com.saludybienestar.agramonte.service.CitaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/citas")
@RequiredArgsConstructor
public class CitaController {

    /** Cabecera que transporta el token de acceso de una cita de invitado. */
    public static final String CABECERA_TOKEN = "X-Cita-Token";

    private final CitaService citaService;
    private final MedicoRepository medicoRepository;

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
     *
     * <p>De quién es la agenda lo decide {@link #agendaDe}, nunca el parámetro.
     */
    @GetMapping("/agenda/pacientes")
    public List<PacienteAgendaResponse> pacientesAgenda(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Long medicoId) {
        return citaService.listarPacientesAgenda(agendaDe(user, medicoId));
    }

    /**
     * Historial de reservas del médico (vista de seguimiento, no es el panel clínico).
     * Contiene datos identificativos del paciente, así que exige JWT con rol MEDICO o ADMIN
     * por la regla {@code /api/citas/agenda/**} de {@code SecurityConfig}.
     *
     * <p>De quién es la agenda lo decide {@link #agendaDe}, nunca el parámetro.
     */
    @GetMapping("/agenda/reservas")
    public List<ReservaPruebaResponse> reservasPrueba(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Long medicoId,
            @RequestParam(required = false) String centroCodigo) {
        return citaService.listarReservasPrueba(agendaDe(user, medicoId), centroCodigo);
    }

    /**
     * Agenda que esta petición puede leer. Antes salía de {@code ?medicoId=}, con un
     * {@code defaultValue = "1"}: bastaba cambiar el número de la URL para que un médico
     * leyera la agenda de otro, con los nombres, correos, teléfonos y motivos de consulta
     * de sus pacientes.
     *
     * <p>A un MEDICO se le da siempre la suya y el parámetro se ignora en silencio, sin
     * responder 403 ni 404: contestar distinto según el id pedido confirmaría qué médicos
     * existen, y los id son enteros correlativos. Si su cuenta no tiene ficha vinculada
     * (columna {@code medicos.usuario_id}, V12) no hay agenda que enseñarle: 403.
     *
     * <p>Un ADMIN sí elige, porque administrar es justamente eso, pero tiene que decir
     * cuál: sin parámetro no se adivina, y el antiguo «médico 1 por defecto» era una
     * suposición disfrazada de valor por defecto.
     */
    private Long agendaDe(CustomUserDetails user, Long medicoIdPedido) {
        if (user == null) {
            // No debería ocurrir: SecurityConfig ya exige rol en /api/citas/agenda/**.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida");
        }
        if (user.getRol() == Rol.ADMIN) {
            if (medicoIdPedido == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Indica de qué médico quieres ver la agenda (medicoId)");
            }
            return medicoIdPedido;
        }
        return medicoRepository.findByUsuarioId(user.getId())
                .map(Medico::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Tu cuenta no tiene ninguna agenda asociada"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarCita(@AuthenticationPrincipal CustomUserDetails user,
                             @PathVariable Long id) {
        citaService.cancelarCita(user.getId(), id);
    }
}