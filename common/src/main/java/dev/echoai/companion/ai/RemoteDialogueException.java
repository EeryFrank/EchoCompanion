package dev.echoai.companion.ai;

/**
 * A deliberately sanitized remote failure. Messages never include request
 * headers, the API key, or the response body.
 */
public final class RemoteDialogueException extends RuntimeException {
    public enum Kind {
        TRANSPORT,
        HTTP_STATUS,
        REDIRECT_REFUSED,
        MALFORMED_RESPONSE,
        RESPONSE_TOO_LARGE
    }

    private final Kind kind;
    private final int statusCode;

    private RemoteDialogueException(Kind kind, String safeMessage, int statusCode, Throwable cause) {
        super(safeMessage, cause);
        this.kind = kind;
        this.statusCode = statusCode;
    }

    public static RemoteDialogueException transport(Throwable cause) {
        return new RemoteDialogueException(
                Kind.TRANSPORT,
                "Remote AI transport request failed.",
                -1,
                cause
        );
    }

    public static RemoteDialogueException httpStatus(int statusCode) {
        return new RemoteDialogueException(
                Kind.HTTP_STATUS,
                "Remote AI returned HTTP status " + statusCode + ".",
                statusCode,
                null
        );
    }

    public static RemoteDialogueException redirectRefused(int statusCode) {
        return new RemoteDialogueException(
                Kind.REDIRECT_REFUSED,
                "Remote AI redirect was refused (HTTP " + statusCode + ").",
                statusCode,
                null
        );
    }

    public static RemoteDialogueException malformedResponse() {
        return new RemoteDialogueException(
                Kind.MALFORMED_RESPONSE,
                "Remote AI returned an invalid Chat Completions response.",
                -1,
                null
        );
    }

    public static RemoteDialogueException responseTooLarge() {
        return new RemoteDialogueException(
                Kind.RESPONSE_TOO_LARGE,
                "Remote AI response exceeded the safety limit.",
                -1,
                null
        );
    }

    public Kind kind() {
        return kind;
    }

    /** Returns {@code -1} when no HTTP response was received. */
    public int statusCode() {
        return statusCode;
    }
}
