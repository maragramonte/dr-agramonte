package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.config.TelegramProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Qué pasa cuando Telegram no contesta.
 *
 * <p>El aviso se envía en el mismo hilo que atiende la reserva. Si la llamada no tiene
 * tiempo máximo, un Telegram colgado deja la reserva colgada con él. Aquí un servidor
 * local acepta la conexión y nunca responde: el envío tiene que rendirse, devolver
 * false y dejar seguir al resto.
 */
class TelegramMessageServiceTest {

    private ServerSocket servidorMudo;
    private final List<Socket> conexiones = new ArrayList<>();

    @AfterEach
    void cerrar() throws IOException {
        for (Socket s : conexiones) {
            s.close();
        }
        if (servidorMudo != null) {
            servidorMudo.close();
        }
    }

    @Test
    @DisplayName("Si Telegram acepta la conexión y no responde, el envío se rinde y devuelve false")
    void telegramColgado_noBloqueaElEnvio() throws IOException {
        servidorMudo = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread aceptador = new Thread(() -> {
            try {
                while (!servidorMudo.isClosed()) {
                    conexiones.add(servidorMudo.accept()); // acepta y no contesta nunca
                }
            } catch (IOException cerrado) {
                // fin del test
            }
        });
        aceptador.setDaemon(true);
        aceptador.start();

        TelegramProperties props = new TelegramProperties();
        props.setEnabled(true);
        props.setBotToken("123456:prueba");
        props.setApiBase("http://127.0.0.1:" + servidorMudo.getLocalPort());
        TelegramMessageService servicio = new TelegramMessageService(props);

        // Con la configuración por defecto tiene que rendirse en segundos, no quedarse
        // esperando para siempre.
        boolean enviado = assertTimeoutPreemptively(Duration.ofSeconds(15),
                () -> servicio.send("987654321", "Recordatorio de prueba"));

        assertThat(enviado).isFalse();
    }
}
