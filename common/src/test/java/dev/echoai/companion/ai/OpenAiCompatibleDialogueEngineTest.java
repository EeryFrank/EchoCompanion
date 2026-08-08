package dev.echoai.companion.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleDialogueEngineTest {
    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void sendsOpenAiCompatibleRequestAndParsesReply() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"Remote hello\"}}]}");
        });
        OpenAiCompatibleDialogueEngine engine = engine(server, "test-secret");
        DialogueContext context = new DialogueContext(
                "Alex",
                "Current question",
                List.of(
                        DialogueMessage.user("Earlier question"),
                        DialogueMessage.assistant("Earlier answer")
                ),
                "Custom system prompt",
                java.util.Map.of()
        );

        DialogueResult result = engine.reply(context).join();

        assertEquals("Remote hello", result.text());
        assertEquals(AiMode.REMOTE, result.actualMode());
        assertEquals("Bearer test-secret", authorization.get());

        JsonObject sent = JsonParser.parseString(requestBody.get()).getAsJsonObject();
        assertEquals("test-model", sent.get("model").getAsString());
        JsonArray messages = sent.getAsJsonArray("messages");
        assertEquals(4, messages.size());
        assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("Custom system prompt", messages.get(0).getAsJsonObject().get("content").getAsString());
        assertEquals("Current question", messages.get(3).getAsJsonObject().get("content").getAsString());
    }

    @Test
    void reportsHttpFailureWithoutCopyingApiKeyOrResponseBody() throws Exception {
        String secret = "sk-never-log-this";
        HttpServer server = startServer(exchange ->
                respond(exchange, 401, "bad key: " + secret));
        OpenAiCompatibleDialogueEngine engine = engine(server, secret);

        RemoteDialogueException failure = remoteFailure(engine.reply(DialogueContext.of("hello")));

        assertEquals(RemoteDialogueException.Kind.HTTP_STATUS, failure.kind());
        assertEquals(401, failure.statusCode());
        assertFalse(failure.toString().contains(secret));
        assertFalse(failure.getMessage().contains("bad key"));
    }

    @Test
    void rejectsMalformedSuccessJson() throws Exception {
        HttpServer server = startServer(exchange -> respond(exchange, 200, "{not-json"));
        OpenAiCompatibleDialogueEngine engine = engine(server, "secret");

        RemoteDialogueException failure = remoteFailure(engine.reply(DialogueContext.of("hello")));

        assertEquals(RemoteDialogueException.Kind.MALFORMED_RESPONSE, failure.kind());
    }

    @Test
    void refusesRedirectWithoutContactingItsTarget() throws Exception {
        AtomicInteger targetCalls = new AtomicInteger();
        HttpServer target = startServer(exchange -> {
            targetCalls.incrementAndGet();
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"should not happen\"}}]}");
        });
        HttpServer redirector = startServer(exchange -> {
            exchange.getResponseHeaders().add("Location", endpoint(target, "/stolen").toString());
            respond(exchange, 307, "");
        });
        OpenAiCompatibleDialogueEngine engine = engine(redirector, "secret");

        RemoteDialogueException failure = remoteFailure(engine.reply(DialogueContext.of("hello")));

        assertEquals(RemoteDialogueException.Kind.REDIRECT_REFUSED, failure.kind());
        assertEquals(0, targetCalls.get());
    }

    @Test
    void permitsOnlyHttpsOrLoopbackHttpEndpoints() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OpenAiCompatibleDialogueEngine(
                        URI.create("http://example.com/v1"),
                        "secret",
                        "model",
                        Duration.ofSeconds(1)
                )
        );

        OpenAiCompatibleDialogueEngine https = new OpenAiCompatibleDialogueEngine(
                URI.create("https://example.com/v1"),
                "secret",
                "model",
                Duration.ofSeconds(1)
        );
        OpenAiCompatibleDialogueEngine local = new OpenAiCompatibleDialogueEngine(
                URI.create("http://localhost:8080/v1"),
                "",
                "model",
                Duration.ofSeconds(1)
        );

        assertTrue(https instanceof DialogueEngine);
        assertTrue(local instanceof DialogueEngine);
    }

    @Test
    void rejectsInjectedClientThatCouldFollowRedirects() {
        HttpClient unsafeClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> new OpenAiCompatibleDialogueEngine(
                        unsafeClient,
                        URI.create("https://example.com/v1"),
                        "secret",
                        "model",
                        Duration.ofSeconds(1)
                )
        );
    }

    @Test
    void rejectsControlCharactersInApiKeyWithoutEchoingTheKey() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new OpenAiCompatibleDialogueEngine(
                        URI.create("https://example.com/v1"),
                        "secret\u0000value",
                        "model",
                        Duration.ofSeconds(1)
                )
        );

        assertFalse(failure.getMessage().contains("secret"));
    }

    @Test
    void rejectsEndpointQueriesThatCouldPersistCredentials() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OpenAiCompatibleDialogueEngine(
                        URI.create("https://example.com/v1?key=must-not-persist"),
                        "",
                        "model",
                        Duration.ofSeconds(1)
                )
        );
    }

    @Test
    void limitsRemoteResponseBytesBeforeBufferingUnboundedData() throws Exception {
        HttpServer server = startServer(exchange -> respond(
                exchange,
                200,
                "{\"choices\":[{\"message\":{\"content\":\"" + "x".repeat(70_000) + "\"}}]}"
        ));

        RemoteDialogueException failure = remoteFailure(engine(server, "secret").reply(DialogueContext.of("hello")));

        assertEquals(RemoteDialogueException.Kind.RESPONSE_TOO_LARGE, failure.kind());
    }

    @Test
    void rejectsOversizedReplyEvenWithinResponseByteLimit() throws Exception {
        HttpServer server = startServer(exchange -> respond(
                exchange,
                200,
                "{\"choices\":[{\"message\":{\"content\":\"" + "x".repeat(9_000) + "\"}}]}"
        ));

        RemoteDialogueException failure = remoteFailure(engine(server, "secret").reply(DialogueContext.of("hello")));

        assertEquals(RemoteDialogueException.Kind.RESPONSE_TOO_LARGE, failure.kind());
    }

    @Test
    void outboundHistoryIsBoundedEvenWhenCallerSuppliesTooMuch() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
        });
        List<DialogueMessage> longHistory = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            longHistory.add(DialogueMessage.user("history-" + index));
        }
        DialogueContext context = new DialogueContext("Alex", "now", longHistory);

        engine(server, "secret").reply(context).join();

        JsonArray messages = JsonParser.parseString(requestBody.get())
                .getAsJsonObject()
                .getAsJsonArray("messages");
        assertEquals(DialogueContext.MAX_HISTORY_MESSAGES + 2, messages.size());
        assertEquals("history-18", messages.get(1).getAsJsonObject().get("content").getAsString());
        assertEquals("now", messages.get(messages.size() - 1).getAsJsonObject().get("content").getAsString());
    }

    private HttpServer startServer(ThrowingHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception failure) {
                exchange.close();
            }
        });
        server.start();
        servers.add(server);
        return server;
    }

    private static OpenAiCompatibleDialogueEngine engine(HttpServer server, String apiKey) {
        return new OpenAiCompatibleDialogueEngine(
                endpoint(server, "/v1"),
                apiKey,
                "test-model",
                Duration.ofSeconds(3)
        );
    }

    private static URI endpoint(HttpServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static RemoteDialogueException remoteFailure(
            java.util.concurrent.CompletableFuture<DialogueResult> future
    ) {
        CompletionException completion = assertThrows(CompletionException.class, future::join);
        return assertInstanceOf(RemoteDialogueException.class, completion.getCause());
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
