package dev.precognition.spring;

public record AuthorizationDecision(boolean granted) {
    public static AuthorizationDecision allow() { return new AuthorizationDecision(true); }
    public static AuthorizationDecision deny() { return new AuthorizationDecision(false); }
}
