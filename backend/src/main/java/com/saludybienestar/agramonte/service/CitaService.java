package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.dto.request.CrearCitaRequest;
import com.saludybienestar.agramonte.dto.request.ReservaPublicaRequest;
import com.saludybienestar.agramonte.dto.response.AuthResponse;
import com.saludybienestar.agramonte.dto.response.CitaResponse;
import com.saludybienestar.agramonte.dto.response.ReservaPublicaResponse;
import com.saludybienestar.agramonte.entity.*;
import com.saludybienestar.agramonte.repository.*;
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

    @Transactional
    public ReservaPublicaResponse reservaPublica(ReservaPublicaRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String passwordPlano = request.getPassword();
        boolean conPassword = passwordPlano != null && !passwordPlano.isBlank();

        Usuario paciente = usuarioRepository.findByEmail(email).orElse(null);

        if (paciente == null) {
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
            if (paciente.getRol() != Rol.PACIENTE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Este email no puede usarse para reservas de paciente");
            }
            if (!paciente.isCuentaInvitada()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Este email ya tiene cuenta. Inicia sesión para reservar.");
            }
            paciente.setNombre(request.getNombre().trim());
            paciente.setTelefono(request.getTelefono().trim());
            if (conPassword) {
                paciente.setPassword(passwordEncoder.encode(passwordPlano));
                paciente.setCuentaInvitada(false);
            }
            paciente = usuarioRepository.save(paciente);
        }

        CrearCitaRequest citaRequest = new CrearCitaRequest();
        citaRequest.setMedicoId(request.getMedicoId());
        citaRequest.setFechaHora(request.getFechaHora());
        citaRequest.setMotivo(request.getMotivo());
        citaRequest.setTelefono(request.getTelefono());
        citaRequest.setCentroCodigo(request.getCentroCodigo());
        citaRequest.setCobertura(request.getCobertura());
        citaRequest.setAseguradora(request.getAseguradora());
        citaRequest.setNumeroTarjetaSanitaria(request.getNumeroTarjetaSanitaria());
        citaRequest.setPreferenciaPago(request.getPreferenciaPago());

        CitaResponse cita = crearCita(paciente.getId(), citaRequest);

        AuthResponse sesion = null;
        if (conPassword) {
            sesion = authService.tokenTrasAutenticacion(email, passwordPlano);
        }
        return new ReservaPublicaResponse(cita, sesion);
    }

    @Transactional
    public CitaResponse crearCita(Long pacienteId, CrearCitaRequest request) {
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

    public List<CitaResponse> findByPacienteEmail(String email) {
        String normalized = email.trim().toLowerCase();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email obligatorio");
        }
        return citaRepository.findByUsuarioEmailWithMedico(normalized).stream()
                .map(this::toResponse)
                .toList();
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
    public void cancelarCitaPublica(String email, Long citaId) {
        String normalized = email.trim().toLowerCase();
        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita no encontrada"));

        if (!cita.getUsuario().getEmail().equalsIgnoreCase(normalized)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El email no coincide con esta cita");
        }

        aplicarCancelacion(cita);
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