package dev.echoai.companion.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Java 21 {@link HttpClient} implementation of the OpenAI-compatible Chat
 * Completions protocol.
 *
 * <p>Redirect following is entirely disabled. This is stricter than merely
 * rejecting cross-host redirects and prevents an Authorization header from
 * being forwarded to any redirect target.</p>
 */
public final class OpenAiCompatibleDialogueEngine implements DialogueEngine {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
    static final int MAX_RESPONSE_BYTES = 65_536;
    static final int MAX_REPLY_CHARACTERS = 8_000;
    public static final String DEFAULT_SYSTEM_PROMPT =
            "You are a friendly companion inside Minecraft. Reply concisely and never claim to perform actions you cannot perform.";

    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public OpenAiCompatibleDialogueEngine(String endpoint, String apiKey, String model) {
        this(URI.create(requireText(endpoint, "endpoint")), apiKey, model, DEFAULT_TIMEOUT);
    }

    public OpenAiCompatibleDialogueEngine(URI endpoint, String apiKey, String model, Duration timeout) {
        this(newHttpClient(timeout), endpoint, apiKey, model, timeout);
    }

    /**
     * Injection-friendly constructor. Clients configured to follow redirects
     * are rejected so the security invariant cannot be weakened by a caller.
     */
    public OpenAiCompatibleDialogueEngine(
            HttpClient httpClient,
            URI endpoint,
            String apiKey,
            String model,
            Duration timeout
    ) {
        this.httpClient = requireNoRedirectClient(httpClient);
        this.endpoint = normalizeAndValidateEndpoint(endpoint);
        this.apiKey = normalizeApiKey(apiKey);
        this.model = requireText(model, "model");
        this.timeout = requirePositiveDuration(timeout);
    }

    @Override
    public CompletableFuture<DialogueResult> reply(DialogueContext context) {
        Objects.requireNonNull(context, "context");

        HttpRequest request;
        CompletableFuture<HttpResponse<String>> pending;
        try {
            request = createRequest(context);
            pending = httpClient.sendAsync(
                    request,
                    limitedUtf8BodyHandler()
            );
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(RemoteDialogueException.transport(failure));
        }

        return pending.handle((response, failure) -> {
            if (failure != null) {
                Throwable cause = unwrap(failure);
                if (cause instanceof RemoteDialogueException remoteFailure) {
                    throw new CompletionException(remoteFailure);
                }
                throw new CompletionException(RemoteDialogueException.transport(cause));
            }
            return parseResponse(response);
        });
    }

    private HttpRequest createRequest(DialogueContext context) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", createMessages(context));

        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));

        if (!apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }

    private JsonArray createMessages(DialogueContext context) {
        JsonArray messages = new JsonArray();
        String systemPrompt = context.systemPrompt().isBlank()
                ? DEFAULT_SYSTEM_PROMPT
                : context.systemPrompt();
        messages.add(message("system", systemPrompt));

        for (DialogueMessage historyMessage : context.history()) {
            String role = historyMessage.role() == DialogueRole.USER ? "user" : "assistant";
            messages.add(message(role, historyMessage.content()));
        }
        messages.add(message("user", context.userMessage()));
        return messages;
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static DialogueResult parseResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        if (statusCode >= 300 && statusCode < 400) {
            throw RemoteDialogueException.redirectRefused(statusCode);
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw RemoteDialogueException.httpStatus(statusCode);
        }

        try {
            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                throw RemoteDialogueException.malformedResponse();
            }
            JsonElement rootElement = JsonParser.parseString(responseBody);
            if (!rootElement.isJsonObject()) {
                throw RemoteDialogueException.malformedResponse();
            }

            JsonArray choices = rootElement.getAsJsonObject().getAsJsonArray("choices");
            if (choices == null || choices.isEmpty() || !choices.get(0).isJsonObject()) {
                throw RemoteDialogueException.malformedResponse();
            }

            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject responseMessage = choice.getAsJsonObject("message");
            if (responseMessage == null) {
                throw RemoteDialogueException.malformedResponse();
            }

            JsonElement content = responseMessage.get("content");
            if (content == null
                    || !content.isJsonPrimitive()
                    || !content.getAsJsonPrimitive().isString()
                    || content.getAsString().isBlank()) {
                throw RemoteDialogueException.malformedResponse();
            }
            String reply = content.getAsString();
            if (reply.length() > MAX_REPLY_CHARACTERS) {
                throw RemoteDialogueException.responseTooLarge();
            }
            return DialogueResult.remote(reply);
        } catch (RemoteDialogueException expected) {
            throw expected;
        } catch (JsonParseException | IllegalStateException | IndexOutOfBoundsException failure) {
            throw RemoteDialogueException.malformedResponse();
        }
    }

    private static HttpClient newHttpClient(Duration timeout) {
        Duration checkedTimeout = requirePositiveDuration(timeout);
        return HttpClient.newBuilder()
                .connectTimeout(checkedTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private static HttpResponse.BodyHandler<String> limitedUtf8BodyHandler() {
        return responseInfo -> new LimitedUtf8BodySubscriber(MAX_RESPONSE_BYTES);
    }

    private static HttpClient requireNoRedirectClient(HttpClient client) {
        Objects.requireNonNull(client, "httpClient");
        if (client.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("httpClient must disable redirects");
        }
        return client;
    }

    static URI normalizeAndValidateEndpoint(URI rawEndpoint) {
        Objects.requireNonNull(rawEndpoint, "endpoint");
        if (!rawEndpoint.isAbsolute() || rawEndpoint.getHost() == null || rawEndpoint.getHost().isBlank()) {
            throw new IllegalArgumentException("endpoint must be an absolute HTTP(S) URI with a host");
        }
        if (rawEndpoint.getUserInfo() != null) {
            throw new IllegalArgumentException("endpoint must not contain user information");
        }
        if (rawEndpoint.getFragment() != null) {
            throw new IllegalArgumentException("endpoint must not contain a fragment");
        }
        if (rawEndpoint.getQuery() != null) {
            throw new IllegalArgumentException("endpoint must not contain a query");
        }

        String scheme = rawEndpoint.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(scheme.equals("http") && isLocalhost(rawEndpoint.getHost()))) {
            throw new IllegalArgumentException("endpoint must use HTTPS, except HTTP is allowed for localhost");
        }

        String path = rawEndpoint.getPath();
        if (path == null || path.isBlank() || path.equals("/")) {
            path = "/v1/chat/completions";
        } else {
            while (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            if (!path.endsWith("/chat/completions")) {
                path += "/chat/completions";
            }
        }

        try {
            return new URI(
                    scheme,
                    rawEndpoint.getUserInfo(),
                    rawEndpoint.getHost(),
                    rawEndpoint.getPort(),
                    path,
                    null,
                    null
            );
        } catch (URISyntaxException impossibleAfterValidation) {
            throw new IllegalArgumentException("endpoint is invalid", impossibleAfterValidation);
        }
    }

    private static boolean isLocalhost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost")
                || normalized.equals("127.0.0.1")
                || normalized.equals("::1")
                || normalized.equals("0:0:0:0:0:0:0:1");
    }

    private static String normalizeApiKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("apiKey must not contain control characters");
        }
        return value.trim();
    }

    private static Duration requirePositiveDuration(Duration value) {
        Objects.requireNonNull(value, "timeout");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** Streams at most a small fixed response into memory, then cancels. */
    private static final class LimitedUtf8BodySubscriber implements HttpResponse.BodySubscriber<String> {
        private final int maximumBytes;
        private final ByteArrayOutputStream output;
        private final CompletableFuture<String> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedUtf8BodySubscriber(int maximumBytes) {
            this.maximumBytes = maximumBytes;
            this.output = new ByteArrayOutputStream(Math.min(maximumBytes, 8_192));
        }

        @Override
        public CompletionStage<String> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription newSubscription) {
            Objects.requireNonNull(newSubscription, "subscription");
            if (subscription != null) {
                newSubscription.cancel();
                return;
            }
            subscription = newSubscription;
            newSubscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer buffer : buffers) {
                int incoming = buffer.remaining();
                if (output.size() + incoming > maximumBytes) {
                    subscription.cancel();
                    body.completeExceptionally(RemoteDialogueException.responseTooLarge());
                    return;
                }
                byte[] chunk = new byte[incoming];
                buffer.get(chunk);
                output.writeBytes(chunk);
            }
        }

        @Override
        public void onError(Throwable failure) {
            body.completeExceptionally(failure);
        }

        @Override
        public void onComplete() {
            body.complete(output.toString(StandardCharsets.UTF_8));
        }
    }
}
