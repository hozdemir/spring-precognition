package dev.precognition.example;

import dev.precognition.spring.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Demo instrumentation, not a persistence layer. */
@Component
public class Probe {
    private final AtomicInteger controllerCalls = new AtomicInteger();
    private final AtomicInteger nameValidationCalls = new AtomicInteger();

    public int recordControllerCall() {
        return controllerCalls.incrementAndGet();
    }

    public void recordNameValidation() {
        nameValidationCalls.incrementAndGet();
    }

    public Snapshot snapshot() {
        return new Snapshot(controllerCalls.get(), nameValidationCalls.get());
    }

    public void reset() {
        controllerCalls.set(0);
        nameValidationCalls.set(0);
    }

    public record Snapshot(int controllerCalls, int nameValidationCalls) {}
}
