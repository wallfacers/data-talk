package com.datatalk.adapter.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConnectionControllerIT {

    @LocalServerPort int port;
    WebTestClient web() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void put_updates_existing_connection() {
        var w = web();
        w.post().uri("/api/connections").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"id":"u1","kind":"mysql","host":"h","port":3306,
                 "database":"d","username":"u","password":"p"}
                """)
            .exchange().expectStatus().isCreated();

        w.put().uri("/api/connections/u1").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"kind":"postgres","host":"h2","port":5432,
                 "database":"d2","username":"u2","password":"p2"}
                """)
            .exchange().expectStatus().isNoContent();

        w.get().uri("/api/connections").exchange()
            .expectBody().jsonPath("$.connections[0].host").isEqualTo("h2");
    }

    @Test
    void delete_returns_204_on_success_404_on_missing() {
        var w = web();
        w.post().uri("/api/connections").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"id":"d1","kind":"mysql","host":"h","port":3306,
                 "database":"d","username":"u","password":"p"}
                """)
            .exchange().expectStatus().isCreated();

        w.delete().uri("/api/connections/d1").exchange().expectStatus().isNoContent();
        w.delete().uri("/api/connections/d1").exchange().expectStatus().isNotFound();
    }

    @Test
    void test_endpoint_returns_ok_false_for_bad_target() {
        var w = web();
        w.post().uri("/api/connections").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"id":"t1","kind":"mysql","host":"127.0.0.1","port":1,
                 "database":"x","username":"u","password":"p"}
                """)
            .exchange().expectStatus().isCreated();
        w.post().uri("/api/connections/t1/test").exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.ok").isEqualTo(false);
    }
}
