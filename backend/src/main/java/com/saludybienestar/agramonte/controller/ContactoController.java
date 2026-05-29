package com.saludybienestar.agramonte.controller;

import com.saludybienestar.agramonte.dto.request.ContactoRequest;
import com.saludybienestar.agramonte.service.ContactoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
public class ContactoController {

    private final ContactoService contactoService;

    @PostMapping
    public ResponseEntity<Map<String, String>> enviar(@Valid @RequestBody ContactoRequest request) {
        contactoService.procesarMensaje(request);
        return ResponseEntity.ok(Map.of(
                "message", "Mensaje recibido correctamente. Nos pondremos en contacto pronto."
        ));
    }
}
