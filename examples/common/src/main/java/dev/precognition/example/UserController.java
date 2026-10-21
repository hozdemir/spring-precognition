package dev.precognition.example;

import dev.precognition.spring.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {
    private final Probe probe;

    public UserController(Probe probe) {
        this.probe = probe;
    }

    @Precognitive
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedUser create(@Valid @RequestBody CreateUser request) {
        return new CreatedUser(probe.recordControllerCall(), request.name(), request.email());
    }

    @PostMapping("/legacy-users")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedUser legacy(@Valid @RequestBody CreateUser request) {
        return new CreatedUser(probe.recordControllerCall(), request.name(), request.email());
    }

    @Precognitive
    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateProfile profile(@Valid @RequestBody CreateProfile request) {
        probe.recordControllerCall();
        return request;
    }

    @GetMapping("/demo/counters")
    public Probe.Snapshot counters() {
        return probe.snapshot();
    }

    public record CreateUser(
            @NotBlank @NameAvailable String name,
            @NotBlank @Email String email,
            @NotNull @Min(18) Integer age) {}

    public record CreatedUser(int id, String name, String email) {}

    public record CreateProfile(@NotBlank @NameAvailable String name, @NotNull @Valid Address address) {}
    public record Address(@NotBlank String city, @NameAvailable String postcode) {}
}
