package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.dto.request.CrearCitaRequest;
import com.saludybienestar.agramonte.dto.request.ReservaPublicaRequest;
import com.saludybienestar.agramonte.dto.response.AuthResponse;
import com.saludybienestar.agramonte.dto.response.CitaResponse;
import com.saludybienestar.agramonte.dto.response.ReservaPublicaResponse;
import com.saludybienestar.agramonte.entity.*;
import com.saludybienestar.agramonte.repository.*;
import com.saludybienestar.agramonte.security.TokenAccesoCita;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.saludybienestar.agramonte.dto.response.CitaAgendaItemResponse;
import com.saludybienestar.agramonte.dto.response.PacienteAgendaResponse;
import com.saludybienestar.agramonte.dto.response.ReservaPruebaResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
@RequiredArgsConstructor
public class CitaService {

    private final CitaRepository citaRepository;
    private final MedicoRepository medicoRepository;
    private final UsuarioRepository usuarioRepository;
    private final HorarioRepository horarioRepository;
    private final CentroRepository centroRepository;
    private final CitaNotificationService citaNotificationService;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final ActivacionCuentaService activacionCuentaService;
    private final CorreoAccesoCitaService correoAccesoCitaService;

    @Transactional
    public ReservaPublicaResponse reservaPublica(ReservaPublicaRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String passwordPlano = request.getPassword();
        boolean conPassword = passwordPlano != null && !passwordPlano.isBlank();

        Usuario paciente = usuarioRepository.findByEmail(email).orElse(null);
        boolean cuentaNueva = paciente == null;
        boolean activacionPendiente = false;

        if (cuentaNueva) {
            paciente = new Usuario();
            paciente.setEmail(email);
            paciente.setNombre(request.getNombre().trim());
            paciente.setTelefono(request.getTelefono().trim());
            paciente.setRol(Rol.PACIENTE);
            paciente.setEnabled(true);
            if (conPassword) {
                paciente.setPassword(passwordEncoder.encode(passwordPlano));
                paciente.setCuentaInvitada(false);
            } else {
                paciente.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                paciente.setCuentaInvitada(true);
            }
            paciente = usuarioRepository.save(paciente);
        } else {
            // Una cuenta que YA existe no se modifica nunca desde esta ruta pública: quien
            // llama solo ha demostrado conocer un email, y un email no es un secreto. Antes
            // este camino sobrescribía el nombre, el teléfono y hasta la contraseña de la
            // cuenta ajena, y devolvía una sesión a nombre de su dueño.
            if (paciente.getRol() != Rol.PACIENTE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Este email no puede usarse para reservas de paciente");
            }
            if (!paciente.isCuentaInvitada()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Este email ya tiene cuenta. Inicia sesión para reservar.");
            }
            if (conPassword) {
                // Poner contraseña es tomar el control de la cuenta, y eso solo puede
                // hacerlo quien abra ese buzón: aquí quien llama únicamente ha demostrado
                // conocer un correo. Antes esto era un 409 que dejaba al invitado sin
                // ninguna salida; ahora la reserva sigue adelante como invitada y se
                // manda un enlace de activación. La contraseña NO se guarda.
                activacionPendiente = true;
            }
        }

        CrearCitaRequest citaRequest = new CrearCitaRequest();
        citaRequest.setMedicoId(request.getMedicoId());
        citaRequest.setFechaHora(request.getFechaHora());
        citaRequest.setMotivo(request.getMotivo());
        // El teléfono NO viaja en el request de la cita: crearCita() lo escribiría sobre la
        // cuenta del paciente, y una cuenta invitada preexistente no se toca. Si la cuenta
        // es nueva, el teléfono ya quedó guardado arriba.
        citaRequest.setCentroCodigo(request.getCentroCodigo());
        citaRequest.setCobertura(request.getCobertura());
        citaRequest.setAseguradora(request.getAseguradora());
        citaRequest.setNumeroTarjetaSanitaria(request.getNumeroTarjetaSanitaria());
        citaRequest.setPreferenciaPago(request.getPreferenciaPago());

        // Token de acceso a ESTA cita: se entrega una sola vez, aquí, y es lo que permite
        // al invitado consultarla y cancelarla sin cuenta. En la BD solo queda su hash.
        // Se emite siempre, también cuando además se abre sesión, para que el acceso a la
        // cita siga funcionando si esa sesión se cierra.
        String tokenAcceso = TokenAccesoCita.generar();
        CitaResponse cita = crearCita(paciente.getId(), citaRequest, TokenAccesoCita.hash(tokenAcceso));

        // Solo se devuelve sesión si la cuenta se acaba de crear con contraseña: para una
        // cuenta preexistente sería entregar un JWT de otra persona.
        AuthResponse sesion = null;
        if (cuentaNueva && conPassword) {
            sesion = authService.tokenTrasAutenticacion(email, passwordPlano);
        }

        // El correo va al final, con la cita ya creada: si el envío falla, la reserva se
        // deshace entera y el paciente lo ve, en vez de quedarse con una cita y sin saber
        // que el enlace nunca salió.
        if (activacionPendiente) {
            activacionCuentaService.solicitarActivacion(paciente);
        }

        // El enlace con el token también va por correo, porque hasta ahora el token solo
        // vivía en el navegador que reservó: quien reservaba desde un móvil prestado o en
        // una ventana privada se quedaba sin acceso a su propia cita, y en la BD solo está
        // el hash, así que no había forma de devolvérselo.
        //
        // Tras el commit y sin poder romper nada: la cita ya está confirmada y el token ya
        // viaja en la respuesta, así que un SMTP caído no debe convertirse en un error para
        // quien acaba de reservar. Mismo trato que los avisos de Twilio y Telegram.
        //
        // Se manda siempre que se emite token, también cuando la reserva abre sesión: el
        // token sigue valiendo si esa sesión se cierra, y el correo hace de confirmación.
        // Los datos se copian a variables locales porque el envío corre fuera de la
        // transacción, cuando la entidad ya está desligada.
        String buzonPaciente = paciente.getEmail();
        String nombrePaciente = paciente.getNombre();
        notificarTrasCommit(() ->
                correoAccesoCitaService.enviar(buzonPaciente, nombrePaciente, cita, tokenAcceso));

        return new ReservaPublicaResponse(cita, sesion, tokenAcceso, activacionPendiente);
    }

    @Transactional
    public CitaResponse crearCita(Long pacienteId, CrearCitaRequest request) {
        return crearCita(pacienteId, request, null);
    }

    /**
     * @param accesoTokenHash hash del token de acceso del invitado, o {@code null} si la
     *                        cita se crea con sesión (el JWT ya identifica a su dueño).
     */
    private CitaResponse crearCita(Long pacienteId, CrearCitaRequest request, String accesoTokenHash) {
        // Invariante de negocio, no solo validación de entrada: una cita en el pasado no
        // existe. La anotación @Future de los DTO cubre la API, pero la regla se repite
        // aquí porque reservaPublica() arma su propio CrearCitaRequest y porque las
        // franjas vencidas siguen en la tabla marcadas como disponibles.
        if (request.getFechaHora() == null || !request.getFechaHora().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede reservar una cita en una fecha ya pasada");
        }

        Usuario paciente = usuarioRepository.findById(pacienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente no encontrado"));
        Medico medico = medicoRepository.findById(request.getMedicoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medico no encontrado"));

        // BLOQUEO PESIMISTA: comprueba si el horario está disponible y lo bloquea
        Horario horario = horarioRepository.findByMedicoIdAndInicioAndDisponibleTrueForUpdate(
                medico.getId(), request.getFechaHora()
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Horario no disponible"));

        if (request.getTelefono() != null && !request.getTelefono().isBlank()) {
            paciente.setTelefono(request.getTelefono().trim());
            usuarioRepository.save(paciente);
        }

        // El centro de la cita lo determina el horario reservado (agenda por centro):
        // cada franja pertenece a un centro. Si el horario no tiene centro (datos
        // antiguos), se usa el código del request como respaldo (compatibilidad).
        Centro centro = horario.getCentro();
        if (centro == null) {
            String centroCodigo = request.getCentroCodigo();
            if (centroCodigo != null && !centroCodigo.isBlank()) {
                centro = centroRepository.findByCodigo(centroCodigo.trim().toLowerCase()).orElse(null);
            }
        }

        // Si llegamos aquí, el horario está libre y lo hemos bloqueado para esta transacción
        Cita cita = new Cita();
        cita.setUsuario(paciente);
        cita.setMedico(medico);
        cita.setCentro(centro);
        cita.setFechaHora(request.getFechaHora());
        cita.setMotivo(request.getMotivo());
        cita.setCobertura(request.getCobertura());
        cita.setAseguradora(request.getAseguradora());
        cita.setNumeroTarjetaSanitaria(request.getNumeroTarjetaSanitaria());
        cita.setPreferenciaPago(request.getPreferenciaPago());
        cita.setAccesoTokenHash(accesoTokenHash);
        cita.setEstado(EstadoCita.CONFIRMADA);

        horario.setDisponible(false);
        horarioRepository.save(horario);

        Cita guardada = citaRepository.save(cita);
        notificarTrasCommit(() -> citaNotificationService.notifyNuevaCita(guardada));
        return toResponse(guardada);
    }

    public List<CitaResponse> findByPaciente(Long id) {
        return citaRepository.findByUsuarioId(id).stream().map(this::toResponse).toList();
    }

    /**
     * Cita de un invitado identificada por su token de acceso. Sustituye a la antigua
     * consulta por email, que entregaba el historial del paciente (con el motivo de
     * consulta, que es dato de salud) a cualquiera que conociera su correo.
     */
    @Transactional(readOnly = true)
    public CitaResponse buscarCitaPublica(Long citaId, String token) {
        return toResponse(citaConTokenValido(citaId, token));
    }

    /**
     * Pacientes con al menos una cita activa (no cancelada) con el médico indicado.
     */
    public List<PacienteAgendaResponse> listarPacientesAgenda(Long medicoId) {
        if (!medicoRepository.existsById(medicoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medico no encontrado");
        }

        Map<Long, PacienteAgendaResponse> porUsuario = new LinkedHashMap<>();

        for (Cita cita : citaRepository.findActivasByMedicoIdWithPaciente(medicoId)) {
            Usuario paciente = cita.getUsuario();
            PacienteAgendaResponse entry = porUsuario.computeIfAbsent(
                    paciente.getId(),
                    id -> new PacienteAgendaResponse(
                            id,
                            paciente.getNombre(),
                            paciente.getEmail(),
                            paciente.getTelefono(),
                            new ArrayList<>()
                    )
            );
            Centro centroCita = cita.getCentro();
            entry.getCitas().add(new CitaAgendaItemResponse(
                    cita.getId(),
                    cita.getFechaHora(),
                    cita.getMotivo(),
                    cita.getEstado(),
                    centroCita != null ? centroCita.getCodigo() : null,
                    centroCita != null ? centroCita.getNombre() : null
            ));
        }

        porUsuario.values().forEach(p ->
                p.getCitas().sort(Comparator.comparing(CitaAgendaItemResponse::getFechaHora).reversed())
        );

        return new ArrayList<>(porUsuario.values());
    }

    /**
     * Todas las reservas del médico (incluye canceladas) para el panel de pruebas del TFG.
     * Si {@code centroCodigo} no es nulo/vacío, filtra por ese centro (agenda por ubicación).
     */
    public List<ReservaPruebaResponse> listarReservasPrueba(Long medicoId, String centroCodigo) {
        if (!medicoRepository.existsById(medicoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medico no encontrado");
        }
        final String centro = (centroCodigo == null || centroCodigo.isBlank())
                ? null : centroCodigo.trim().toLowerCase();
        return citaRepository.findAllByMedicoIdWithPaciente(medicoId).stream()
                .filter(c -> centro == null
                        || (c.getCentro() != null && centro.equals(c.getCentro().getCodigo())))
                .map(c -> new ReservaPruebaResponse(
                        c.getId(),
                        c.getUsuario().getId(),
                        c.getUsuario().getNombre(),
                        c.getUsuario().getEmail(),
                        c.getUsuario().getTelefono(),
                        c.getMedico().getId(),
                        c.getMedico().getNombre(),
                        c.getFechaHora(),
                        c.getMotivo(),
                        c.getEstado(),
                        c.getCentro() != null ? c.getCentro().getCodigo() : null,
                        c.getCentro() != null ? c.getCentro().getNombre() : null
                ))
                .toList();
    }

    @Transactional
    public void cancelarCita(Long pacienteId, Long citaId) {
        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita no encontrada"));

        if (!cita.getUsuario().getId().equals(pacienteId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para cancelar esta cita");
        }

        aplicarCancelacion(cita);
    }

    @Transactional
    public void cancelarCitaPublica(Long citaId, String token) {
        aplicarCancelacion(citaConTokenValido(citaId, token));
    }

    /**
     * Carga la cita y exige que el token presentado sea exactamente el de esa cita.
     * Cuando no cuadra responde 404 y no 403: un 403 confirmaría que la cita existe, y
     * el id es un entero correlativo trivial de recorrer.
     */
    private Cita citaConTokenValido(Long citaId, String token) {
        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita no encontrada"));

        // Dos tokens abren la misma cita: el de la reserva y el del correo de
        // recordatorio (V14). Los dos se comprueban en tiempo constante y siempre los
        // dos, sin cortocircuito: parar en el primer acierto haría que el tiempo de
        // respuesta delatara cuál de ellos se ha presentado.
        boolean esElDeLaReserva = TokenAccesoCita.coincide(token, cita.getAccesoTokenHash());
        boolean esElDelRecordatorio = TokenAccesoCita.coincide(token, cita.getRecordatorioTokenHash());

        if (!esElDeLaReserva && !esElDelRecordatorio) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita no encontrada");
        }
        return cita;
    }

    private void aplicarCancelacion(Cita cita) {
        if (cita.getEstado() == EstadoCita.CANCELADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cita ya esta cancelada");
        }

        cita.setEstado(EstadoCita.CANCELADA);

        horarioRepository.findByMedicoIdAndInicio(
                cita.getMedico().getId(), cita.getFechaHora()
        ).ifPresent(h -> h.setDisponible(true));

        citaRepository.save(cita);
        notificarTrasCommit(() -> citaNotificationService.notifyCancelacion(cita));
    }

    /**
     * Ejecuta el envío de notificaciones DESPUÉS de confirmarse la transacción (commit).
     * Así no se mantiene el bloqueo pesimista del horario durante la llamada de red a Twilio
     * y nunca se notifica una cita que finalmente no se persiste (si la transacción revierte).
     * Si no hay transacción activa (p. ej. pruebas unitarias) se ejecuta en el acto.
     */
    private void notificarTrasCommit(Runnable notificacion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificacion.run();
                }
            });
        } else {
            notificacion.run();
        }
    }

    private CitaResponse toResponse(Cita cita) {
        Centro centro = cita.getCentro();
        return new CitaResponse(
                cita.getId(),
                cita.getUsuario().getId(),
                cita.getMedico().getId(),
                cita.getMedico().getNombre(),
                cita.getFechaHora(),
                cita.getMotivo(),
                cita.getEstado(),
                centro != null ? centro.getCodigo() : null,
                centro != null ? centro.getNombre() : null,
                cita.getCobertura(),
                cita.getAseguradora(),
                cita.getNumeroTarjetaSanitaria(),
                cita.getPreferenciaPago()
        );
    }

}