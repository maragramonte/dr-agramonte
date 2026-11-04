package com.saludybienestar.agramonte.controller;

import com.saludybienestar.agramonte.dto.request.ActivacionRequest;
import com.saludybienestar.agramonte.dto.request.LoginRequest;
import com.saludybienestar.agramonte.dto.request.RecuperarRequest;
import com.saludybienestar.agramonte.dto.request.RegistroRequest;
import com.saludybienestar.agramonte.dto.response.AuthResponse;
import com.saludybienestar.agramonte.dto.response.MensajeResponse;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.service.ActivacionCuentaService;
import com.saludybienestar.agramonte.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** La misma respuesta para un correo libre y para uno con cuenta: no se distinguen. */
    private static final String REVISA_TU_CORREO =
            "Revisa tu correo: te hemos enviado un enlace para terminar de crear la cuenta.";

    private static final String SI_HAY_CUENTA =
            "Si ese correo tiene cuenta, te hemos enviado un enlace para elegir una contraseña.";

    private final AuthService authService;
    private final ActivacionCuentaService activacionCuentaService;

    public AuthController(AuthService authService, ActivacionCuentaService activacionCuentaService) {
        this.authService = authService;
        this.activacionCuentaService = activacionCuentaService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Alta de paciente: siempre 202, porque lo que sigue es abrir el enlace del correo. */
    @PostMapping("/registro")
    public ResponseEntity<MensajeResponse> registro(@Valid @RequestBody RegistroRequest request) {
        activacionCuentaService.solicitarAlta(request);
        return ResponseEntity.accepted().body(new MensajeResponse(REVISA_TU_CORREO));
    }

    /** Contraseña olvidada: siempre 202, exista o no la cuenta. */
    @PostMapping("/recuperar")
    public ResponseEntity<MensajeResponse> recuperar(@Valid @RequestBody RecuperarRequest request) {
        activacionCuentaService.solicitarRecuperacion(request.getEmail());
        return ResponseEntity.accepted().body(new MensajeResponse(SI_HAY_CUENTA));
    }

    /**
     * Segundo paso del alta y de la recuperación: el token que llegó por correo más la
     * contraseña nueva. Es público porque quien lo usa todavía no tiene sesión; la
     * credencial es el propio token.
     */
    @PostMapping("/activar")
    public ResponseEntity<AuthResponse> activar(@Valid @RequestBody ActivacionRequest request) {
        Usuario activado = activacionCuentaService.activar(request.getToken(), request.getPassword());
        // La sesión se abre aquí y no dentro del servicio de activación: así ese servicio
        // no depende de AuthService, que a su vez lo necesita para el alta de un invitado.
        return ResponseEntity.ok(authService.tokenTrasAutenticacion(activado.getEmail(), request.getPassword()));
    }
}
