package io.restapigen.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestApiGeneratorServerAboutTest {

    private RestApiGeneratorServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        server = new RestApiGeneratorServer(port);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void aboutReturns200WithProjectInfo() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create("http://localhost:" + port + "/about").toURL().openConnection();
        conn.setRequestMethod("GET");
        int status = conn.getResponseCode();
        String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        conn.disconnect();

        assertEquals(200, status);
        assertTrue(conn.getContentType().contains("application/json"));
        assertTrue(body.contains("REST API Generator"));
        assertTrue(body.contains("/generator/spec"));
        assertTrue(body.contains("/generator/code"));
    }

    @Test
    void aboutReturns405ForPost() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create("http://localhost:" + port + "/about").toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        int status = conn.getResponseCode();
        conn.disconnect();

        assertEquals(405, status);
    }
}
