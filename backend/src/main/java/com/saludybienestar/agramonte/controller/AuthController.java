package com.saludybienestar.agramonte.controller;

import com.saludybienestar.agramonte.dto.request.ActivacionRequest;
import com.saludybienestar.agramonte.dto.request.LoginRequest;
import com.saludybienestar.agramonte.dto.request.RegistroRequest;
import com.saludybienestar.agramonte.dto.response.AuthResponse;
import com.saludybienestar.agramonte.dto.response.MensajeResponse;
import com.saludybienestar.agramonte.dto.response.ResultadoRegistro;
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

    /**
     * Lo que se contesta cuando el correo ya tenía cuenta invitada. Es deliberadamente
     * vago sobre el estado de la cuenta: quien pide el alta solo ha demostrado conocer un
     * email, y no tiene por qué ser su dueño.
     */
    private static final String REVISA_TU_CORREO =
            "Revisa tu correo: te hemos enviado un enlace para terminar de crear la cuenta.";

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

    /**
     * Alta de paciente. Devuelve 200 con la sesión si la cuenta se acaba de crear, y 202
     * si el correo ya tenía una cuenta invitada: ahí no se ha creado ni modificado nada,
     * solo se ha enviado un enlace de activación a ese buzón.
     */
    @PostMapping("/registro")
    public ResponseEntity<?> registro(@Valid @RequestBody RegistroRequest request) {
        ResultadoRegistro resultado = authService.registro(request);
        if (resultado.activacionPendiente()) {
            return ResponseEntity.accepted().body(new MensajeResponse(REVISA_TU_CORREO));
        }
        return ResponseEntity.ok(resultado.sesion());
    }

    /**
     * Segundo paso del alta de una cuenta invitada: el token que llegó por correo más la
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
