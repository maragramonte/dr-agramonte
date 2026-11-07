package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.dto.request.LoginRequest;
import com.saludybienestar.agramonte.dto.response.AuthResponse;
import com.saludybienestar.agramonte.security.CustomUserDetails;
import com.saludybienestar.agramonte.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        return tokenTrasAutenticacion(email, request.getPassword());
    }

    /**
     * Comprueba la contraseña y emite el JWT. No se guarda nada en el
     * SecurityContext: la API es stateless y el contexto muere con la petición.
     */
    public AuthResponse tokenTrasAutenticacion(String email, String passwordPlano) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, passwordPlano));
        CustomUserDetails usuario = (CustomUserDetails) auth.getPrincipal();
        return new AuthResponse(
                jwtProvider.generateToken(auth),
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombre(),
                usuario.getRol().name());
    }
}
