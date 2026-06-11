package com.saludybienestar.agramonte.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba que {@link GlobalExceptionHandler} traduce una
 * {@link NoResourceFoundException} (ruta/recurso inexistente) a un 404 con
 * cuerpo JSON neutro, en lugar de caer en el handler genérico (500 + traza).
 */
class GlobalExceptionHandlerTest {

    @RestController
    static class RutaInexistenteController {
        @GetMapping("/lanza-not-found")
        public void lanza() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/no-existe");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new RutaInexistenteController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("NoResourceFoundException se traduce a 404 con JSON neutro")
    void recursoInexistente_devuelve404() throws Exception {
        mockMvc.perform(get("/lanza-not-found").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
