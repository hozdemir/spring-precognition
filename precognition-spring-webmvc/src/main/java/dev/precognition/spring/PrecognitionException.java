package dev.precognition.spring;

/** Stable protocol error; exception details are never serialized to the client. */
public final class PrecognitionException extends RuntimeException {
    private final int status;
    private final String code;
    public PrecognitionException(int status, String code) { super(code); this.status = status; this.code = code; }
    public int status() { return status; }
    public String code() { return code; }
}
