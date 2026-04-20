package com.datatalk.adapter.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Objects;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConnectionControllerIT {

    @LocalServerPort int port;
    @Autowired ObjectMapper om;

    WebTestClient web() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String createConnection(WebTestClient w, String body) throws Exception {
        byte[] res = w.post().uri("/api/connections").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange().expectStatus().isCreated()
            .expectBody().returnResult().getResponseBody();
        JsonNode node = om.readTree(Objects.requireNonNull(res));
        return node.get("id").asText();
    }

    @Test
    void put_updates_existing_connection() throws Exception {
        var w = web();
        String id = createConnection(w, """
            {"name":"测试数据源","kind":"mysql","host":"h","port":3306,
             "database":"d","username":"u","password":"p"}
            """);

        w.put().uri("/api/connections/" + id).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"name":"更新数据源","kind":"postgres","host":"h2","port":5432,
                 "database":"d2","username":"u2","password":"p2"}
                """)
            .exchange().expectStatus().isNoContent();

        w.get().uri("/api/connections").exchange()
            .expectBody().jsonPath("$.connections[?(@.id=='" + id + "')].host").isEqualTo("h2");
    }

    @Test
    void delete_returns_204_on_success_404_on_missing() throws Exception {
        var w = web();
        String id = createConnection(w, """
            {"name":"删除测试","kind":"mysql","host":"h","port":3306,
             "database":"d","username":"u","password":"p"}
            """);

        w.delete().uri("/api/connections/" + id).exchange().expectStatus().isNoContent();
        w.delete().uri("/api/connections/" + id).exchange().expectStatus().isNotFound();
    }

    @Test
    void test_endpoint_returns_ok_false_for_bad_target() throws Exception {
        var w = web();
        String id = createConnection(w, """
            {"name":"测试连接","kind":"mysql","host":"127.0.0.1","port":1,
             "database":"x","username":"u","password":"p"}
            """);
        w.post().uri("/api/connections/" + id + "/test").exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.ok").isEqualTo(false);
    }

    @Test
    void create_returns_409_on_duplicate_name() throws Exception {
        var w = web();
        createConnection(w, """
            {"name":"唯一名称测试","kind":"mysql","host":"h","port":3306,
             "database":"d","username":"u","password":"p"}
            """);

        w.post().uri("/api/connections").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"name":"唯一名称测试","kind":"postgres","host":"h2","port":5432,
                 "database":"d2","username":"u2","password":"p2"}
                """)
            .exchange().expectStatus().isEqualTo(409);
    }

    @Test
    void create_uses_en_locale_for_default_name() throws Exception {
        var w = web();
        byte[] res = w.post().uri("/api/connections")
            .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"name":"","kind":"mysql","host":"h","port":3306,
                 "database":"d","username":"u","password":"p"}
                """)
            .exchange().expectStatus().isCreated()
            .expectBody().returnResult().getResponseBody();
        String id = om.readTree(Objects.requireNonNull(res)).get("id").asText();

        w.get().uri("/api/connections")
            .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.connections[?(@.id=='" + id + "')].name").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.startsWith("Data Source-")));
    }
}
