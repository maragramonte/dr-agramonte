package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.dto.request.LoginRequest;
import com.saludybienestar.agramonte.dto.request.RegistroRequest;
import com.saludybienestar.agramonte.dto.response.AuthResponse;
import com.saludybienestar.agramonte.dto.response.ResultadoRegistro;
import com.saludybienestar.agramonte.entity.Rol;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.UsuarioRepository;
import com.saludybienestar.agramonte.security.CustomUserDetails;
import com.saludybienestar.agramonte.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final ActivacionCuentaService activacionCuentaService;

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        String token = jwtProvider.generateToken(auth);
        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        return new AuthResponse(
                token,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getNombre(),
                userDetails.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "")
        );
    }

    /**
     * Alta de paciente. Tiene tres finales según lo que haya ya con ese correo:
     *
     * <ul>
     *   <li>Nada: se crea la cuenta y se devuelve sesión, como siempre.</li>
     *   <li>Una cuenta <strong>invitada</strong> (nacida de una reserva sin registro): no
     *       se toca nada y se manda un correo de activación. Antes aquí salía un 409 y
     *       quien había reservado como invitado se quedaba fuera de su propia cuenta para
     *       siempre, sin ninguna vía de recuperación.</li>
     *   <li>Una cuenta normal: sigue siendo 409. Ya tiene dueño y contraseña; lo que
     *       necesita es iniciar sesión, no registrarse.</li>
     * </ul>
     */
    @Transactional
    public ResultadoRegistro registro(RegistroRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        Usuario existente = usuarioRepository.findByEmail(email).orElse(null);
        if (existente != null) {
            if (!activacionCuentaService.esCuentaInvitada(existente)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
            }
            // Ni contraseña, ni nombre, ni teléfono: lo único que cambia es que queda un
            // token de activación pendiente. Quien pide el registro solo ha demostrado
            // conocer un correo, y un correo no es un secreto.
            activacionCuentaService.solicitarActivacion(existente);
            return ResultadoRegistro.activacionEnviada();
        }

        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setNombre(request.getNombre());
        usuario.setPassword(passwordEncoder.encode(request.getPassword()));
        usuario.setTelefono(request.getTelefono());
        usuario.setRol(Rol.PACIENTE);
        usuario.setEnabled(true);
        usuario = usuarioRepository.save(usuario);

        // Autenticación automática después del registro
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );
        String token = jwtProvider.generateToken(auth);
        return ResultadoRegistro.conSesion(new AuthResponse(
                token,
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getRol().name()
        ));
    }

    public AuthResponse tokenTrasAutenticacion(String email, String passwordPlano) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, passwordPlano)
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        return new AuthResponse(
                jwtProvider.generateToken(auth),
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getNombre(),
                userDetails.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "")
        );
    }
}