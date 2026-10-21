# Spring Precognition

**Live validation for Spring MVC forms, using your backend rules without duplicating them in the frontend.** The same endpoint handles both requests: a normal submission performs the operation; a request with `Precognition: true` validates the input and returns before invoking the controller method.

This repository contains the shared Java API, the Spring MVC implementation, Spring Boot 3 and 4 starters, example applications, and HTTP and browser verification suites. It is licensed under the [MIT License](LICENSE).

**Latest release:** `0.1.0` is published to Maven Central. The Maven group ID is `io.github.hozdemir`; the Java API package is `dev.precognition.spring`. See [Verification status](#verification-status) for tested versions, completed checks, and verification limits.

## Why this project exists

Consider a registration form. The backend requires a valid email address, a minimum age, and an available username. Showing those errors only after submission slows down the interaction. Reimplementing the rules in JavaScript creates a second validation system: a change to a Java constraint also needs a frontend change. Rules that depend on a database or tenant context may not be suitable for client-side execution at all.

A separate `/validate` endpoint is a common solution, but it introduces another opportunity for drift. The validation endpoint and the submission endpoint can end up using different DTOs, converters, locales, or authorization policies. Maintaining both paths becomes part of every validation change.

Spring Precognition keeps that interaction on the submission endpoint. As the user edits an email address, the frontend sends a validation request to the normal registration URL. Spring uses its existing JSON binding infrastructure, evaluates the selected field's constraints, and returns without invoking the controller. When the user submits the form, the normal Spring flow runs and validates the complete model again.

The backend remains the owner of the rules. The frontend gets feedback from those rules while the user is editing, and validators on unselected fields do not run. This also avoids unnecessary work such as a username availability query when the user is only checking an email address. A selected validator can still be expensive; the library does not make arbitrary validation code cheap or side-effect-free.

## Inspiration: Laravel Precognition

The starting point for this project is [Laravel Precognition](https://laravel.com/docs/13.x/precognition): validate a route's input and return a result without executing its controller action. Its form helpers provide live feedback using the server's validation rules.

Our goal is to bring that developer experience to Jakarta Validation and Spring MVC. Laravel's middleware and FormRequest lifecycle differ from Spring's handler adapters, argument resolvers, and binders, so the integration is implemented around Spring's own request processing. The HTTP headers and 204/422 response flow are exercised with the official Laravel JavaScript clients.

This is an independent project. It is not an official package or an endorsed integration from the Laravel or Spring teams. Laravel's backend implementation has not been copied into this repository. The official `laravel-precognition`, React, and Vue packages are used as npm dependencies and retain their own licenses.

References: [Laravel documentation](https://laravel.com/docs/13.x/precognition), [Laravel Precognition source](https://github.com/laravel/precognition), and the [Spring Boot auto-configuration guide](https://docs.spring.io/spring-boot/3.5/reference/features/developing-auto-configuration.html).

## Where it runs

Add the library JARs to **your Spring application's dependencies**. The code runs inside the application. It does not require a separate validation server, agent, container, database table, or operating system service.

Maven or Gradle resolves the published starter and its transitive dependencies from Maven Central. When you package your application, its required library JARs travel with the other runtime dependencies. Deploy the application artifact as usual.

## Installation

Java 17+ is required. Add the published dependency directly to your application; cloning or building this repository is optional. See the [installation guide](docs/INSTALLATION.md) for local source builds and repository overrides.

### 1. Add the starter for your Spring Boot version

**Spring Boot 3.5:**

```xml
<dependency>
  <groupId>io.github.hozdemir</groupId>
  <artifactId>precognition-spring-boot3-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

**Spring Boot 4.1:**

```xml
<dependency>
  <groupId>io.github.hozdemir</groupId>
  <artifactId>precognition-spring-boot4-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

Choose one starter. Keep your application's own Spring Boot parent or BOM; you do not need to use this library's parent POM. The Boot 3 adapter uses Jackson 2, and the Boot 4 adapter uses Jackson 3.

For a Gradle Kotlin DSL consumer:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // For Boot 3.5, use precognition-spring-boot3-starter instead.
    implementation("io.github.hozdemir:precognition-spring-boot4-starter:0.1.0")
}
```

The starter brings in MVC, validation, and auto-configuration dependencies. You do not need to expand component scanning or manually import library configuration. An application's existing `WebMvcRegistrations` is preserved. If an opted-in endpoint requires the library adapter but `@EnableWebMvc` or a custom adapter prevents its installation, startup fails with an explicit configuration error. Applications without opted-in endpoints retain their existing MVC registration.

### 2. Opt in an endpoint

```java
import dev.precognition.spring.Precognitive;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @Precognitive
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUser request) {
        return users.create(request);
    }

    public record CreateUser(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotNull @Min(18) Integer age
    ) {}
}
```

`UserService` and `UserResponse` are application-owned types. A valid normal submission calls `users.create`. A precognitive request skips that call. Opt-in is per endpoint; other endpoints retain their normal submission behavior.

### 3. Validate through the same endpoint

```sh
curl -i http://localhost:8080/users \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H 'Precognition: true' \
  -H 'Precognition-Validate-Only: email' \
  -d '{"name":"","email":"invalid","age":12}'
```

Your application's authentication and CSRF requirements apply to validation requests too. In this example, only the email constraints run:

```json
{
  "message": "The given data was invalid.",
  "errors": {
    "email": ["must be a well-formed email address"]
  }
}
```

Validation errors return `422`. The actual messages depend on the application's locale and validation provider configuration. Success returns `204` with an empty body, `Precognition: true`, and `Precognition-Success: true`. Validation responses are not cached.

Omit the selection header to validate the complete model. A present but empty selection header runs no constraints; the supported request body is still bound. Unknown fields return 400, and endpoints that have not opted in return 412. An invalid precognitive request never falls through to a normal submission.

## React and Vue

Use the official form helpers:

```sh
npm install laravel-precognition-react@2.1.0
# Or:
npm install laravel-precognition-vue@2.1.0
```

A React example:

```jsx
import { useForm } from 'laravel-precognition-react';

export function UserForm() {
    const form = useForm('post', '/users', {
        name: 'Ada', email: '', age: 21,
    });

    return <form onSubmit={event => {
        event.preventDefault();
        form.submit();
    }}>
        <input
            value={form.data.email}
            onChange={event => form.setData('email', event.target.value)}
            onBlur={() => form.validate('email')}
        />
        <p>{form.errors.email}</p>
        <button disabled={form.processing}>Save</button>
    </form>;
}
```

In Vue, create `useForm` with the same method, URL, and initial data, update the field through `form.email`, and call `form.validate('email')`. Runnable React and Vue pages are available in [validation/browser](validation/browser). The browser suites connect them to both example backends over real HTTP.

The official helper may skip `validate('email')` when the field has not changed. To explicitly request validation for a selection, use `form.validate({ only: ['email'] })`. Configure authentication and CSRF headers through your application's transport setup. The fixed token used by the browser examples is demonstration code only.

## Validation modes and model support

The default `SELECTIVE` mode accepts field selection. For a model with class-level constraints, make the endpoint full-only:

```java
@Precognitive(mode = PrecognitionMode.FULL)
@PostMapping("/passwords")
public Object change(@Valid @RequestBody ChangePassword request) {
    return service.change(request);
}
```

A FULL endpoint rejects a selection header with 400. This prevents a cross-field rule from being silently skipped during selected validation. Global validation errors appear under `_form`.

Supported bodies include Java records and ordinary DTOs. String, boxed numbers, boolean and character values, BigDecimal, BigInteger, enums, UUID, and supported standard date/time scalar types are bound through the application's converter. Primitive DTO properties are rejected because missing values can silently become `0` or `false`.

`@JsonProperty("first_name")` and the application's standard naming strategy determine the names used for selection and errors. Clients send JSON wire names, not Java property names. The body parameter must declare `@Valid` or `@Validated` using the Default group.

Nested DTOs and records connected by direct `@Valid` associations are supported. Selecting `address.city` validates that leaf; selecting `address` validates the entire subtree. Selecting both a parent and a child does not repeat the rules. Graphs are limited to eight nested levels and the configured field count. A null optional cascade is skipped; if a required parent is null, its property constraint is reported.

Collections, arrays, maps, Optional, wildcard/index paths, JSON aliases, unwrapped or polymorphic models, custom validation groups, multipart requests, WebFlux, async/streaming handlers, and native/AOT are outside the supported scope of this version. Unsupported opt-in signatures that can be detected statically fail at startup. This project does not claim complete feature parity with Laravel Precognition.

## Security and side effects

The library does not install an authentication system or a SecurityFilterChain. Existing filters, URL authorization, and CSRF checks remain in effect. Early 401/403 responses retain their status, body, and authentication challenge; a protocol header lets the official client recognize the response and invoke its status callback.

Skipping the controller also skips the invocation boundary used by controller AOP method security. For that reason, opting in a handler affected by `@PreAuthorize`, `@Secured`, `@RolesAllowed`, or related security metadata causes startup to fail. Share authorization through the security filter chain or the authorizer SPI:

```java
@Bean
PrecognitionAuthorizer authenticatedUserPolicy() {
    return request -> new AuthorizationDecision(request.principal() != null);
}
```

Authorizers run on both normal and precognitive requests to opted-in endpoints, before JSON binding. They receive route templates, path variables, principal, and locale metadata. A denial returns 403. Body-dependent authorization is outside this SPI's scope.

Skipping the controller does not make every other part of the application side-effect-free. Filters, converters, DTO constructors, and custom validators may still execute. Keep database mutations, event publication, and external reservations out of validation code. Precognitive success does not guarantee a later submission will succeed: changing data and races still require validation and database constraints on the final submission.

## Configuration

```yaml
precognition:
  enabled: true
  normal-errors: laravel
  limits:
    max-body-bytes: 65536
    max-selection-header-bytes: 4096
    max-selected-fields: 64
    max-field-name-length: 128
    max-dto-fields: 128
    max-violation-count: 512
    max-response-bytes: 65536
  observability:
    metrics-enabled: true
```

`enabled=false` rejects precognitive requests with 412 rather than invoking the controller. Normal submissions, shared authorizers, and the normal error policy continue to work. Excluding auto-configuration entirely is different from disabling precognition through this property.

`normal-errors=laravel` converts input validation errors on normal submissions to opted-in endpoints into 422 responses with `message/errors`. Set it to `preserve` to retain Spring's or the application's existing error policy. Controller and service exceptions are not globally converted into validation errors.

The body limit covers the complete body, including chunked transfers and trailing whitespace after the JSON value. After shared authorizers allow the request and before binding begins, at most limit+1 bytes are read to check the size. An accepted body is passed to the existing converter through a bounded in-memory buffer. Normal submissions do not use this buffer. If a validation response exceeds its size limit, the library returns a stable 500 error instead of a partial 422 response. These limits do not bound a custom validator's CPU time, database queries, or external service calls.

If the application supplies an ObservationRegistry, the integration emits `precognition.request` observations with `scope` and `status` tags. Raw URLs, selected fields, payloads, and user identities are not tagged. The application's registry handlers determine how observations reach metrics or tracing infrastructure. Without a registry, the integration is a no-op.

Custom validators can use `PrecognitionContextAccessor` to read whether the current request is precognitive, its scope, selected wire fields, and locale. Outside a request, the accessor returns `Optional.empty()`. This context does not introduce global mutable validation state.

## Repository layout

| Directory | Responsibility |
| --- | --- |
| `precognition-core` | Public annotation, modes, context, and authorization API; no Servlet or Boot dependency |
| `precognition-spring-webmvc` | Protocol, endpoint descriptors and verifier, JSON field dictionary, invocation, and validation engine |
| `precognition-spring-boot3-autoconfigure` | Boot 3 / Jackson 2 integration |
| `precognition-spring-boot3-starter` | Dependency entry point for Boot 3 consumers |
| `precognition-spring-boot4-autoconfigure` | Boot 4 / Jackson 3 integration |
| `precognition-spring-boot4-starter` | Dependency entry point for Boot 4 consumers |
| `examples/boot3`, `examples/boot4` | Separate applications consuming the starters |
| `examples/common` | Shared example application sources |
| `verification` | MVC, startup, Security, and HTTP tests run against both platforms |
| `validation` | Official JavaScript core client and React/Vue browser suites |

Boot 4 modules use their own Boot 4 parent. They do not inherit Boot 3 dependency management, which would mix framework major versions on the same classpath.

## Running the examples and contributing

After local installation:

```sh
./scripts/run-example.sh boot4
# Or:
./scripts/run-example.sh boot3
```

The example authentication filter expects `Authorization: Bearer demo-token` on `/users`, `/profiles`, and `/legacy-users`. `GET /demo/counters` returns controller and validator invocation counts. The examples use no database; these counters provide observable verification and are not included in the starter JARs.

```sh
mvn test
mvn -Psecurity-verification test
mvn -Dtest=PrecognitionHttpIT test
./scripts/verify-external.sh
```

The full verification pipeline requires loopback sockets, npm/Maven access, and Chrome. To use Playwright Chromium instead, install it first and set `PRECOGNITION_BROWSER=chromium`. Standalone consumer checks create temporary projects that resolve installed JARs without the reactor's source classpath.

The tests check more than response status: the controller must not execute during validation, unselected validators must be skipped, a subsequent normal submission must perform full validation, parallel requests must not share scope or locale, and official form helpers must update their error state correctly. Library modules without tests allow reactor-wide test selectors; example modules fail if a requested test is missing.

## Verification status

The complete external pipeline passed on October 21, 2026, for Maven group `io.github.hozdemir` at release version `0.1.0`, on Java 17.0.20, using Spring Boot 3.5.16 and 4.1.1:

| Check | Across both Boot versions |
| --- | --- |
| MVC, startup, model, starter, and regression tests | 156 passed |
| Java HTTP tests against embedded Tomcat | 32 passed |
| Actual Spring Security chains with MockMvc | 16 passed |
| Official core client contract replay | 14 passed |
| Official core client over live HTTP | 18 passed |
| React/Vue in a real browser, including cross-origin requests | 20 passed |
| Standalone consumers resolving installed starter JARs | 2 passed |
| **Total** | **258 passed** |

The pipeline also built, installed, and checked all 18 library artifacts: six runtime JARs, six source JARs, and six Javadoc JARs. Runtime checks include coordinates, license, and distribution contents. All 18 files matched their installed copies in the workspace's `.tools/m2` repository. Standalone consumers resolved the new coordinates' installed JARs without the library's reactor classes or manual configuration imports and passed real HTTP tests.

The signed Central bundle contains seven POMs and 18 JARs. All 25 detached signatures and 100 checksum files were verified locally; bundled payloads match the built artifacts and source POMs. Example applications are excluded. Version `0.1.0` completed Central validation and publication.

A separate post-publication check started with an empty Maven local repository and downloaded the released dependencies from Maven Central. Both standalone consumers passed real HTTP tests on Java 17.0.20 with Boot 3.5.16 and 4.1.1: selected-field validation returned 204 without invoking the controller, and a normal submission returned 200 and invoked it once. The two additional tests passed with zero failures, errors, or skips. Their classpaths contained the published library JARs, without library reactor classes or manual configuration imports.

The earlier [Verify workflow run](https://github.com/hozdemir/spring-precognition/actions/runs/39649289102) succeeded with the configured Java 17/21 backend matrix and browser job before the namespace migration. CI results for the final `0.1.0` release source are not yet recorded.

The tested dependency versions were:

| Component | Boot 3 | Boot 4 |
| --- | --- | --- |
| Spring Boot | 3.5.16 | 4.1.1 |
| Spring MVC | 6.2.19 | 7.0.9 |
| Jackson databind | 2.21.4 | 3.1.5 |
| Hibernate Validator | 8.0.3.Final | 9.1.3.Final |
| Spring Security | 6.5.11 | 7.1.1 |
| Embedded Tomcat | 10.1.55 | 11.0.24 |

The official `laravel-precognition`, React, and Vue clients were tested at version 2.1.0, with React 19.2.0, Vue 3.5.27, and Playwright 1.64.1. These results cover the listed versions and supported features; they do not establish load, endurance, or compatibility guarantees for every Servlet container or Spring customization.

## Documentation and contributing

- [Installation and consumer projects](docs/INSTALLATION.md)
- [Architecture and design decisions](docs/ARCHITECTURE.md)
- [HTTP protocol and CORS](docs/PROTOCOL.md)
- [Contributing](CONTRIBUTING.md), [security reporting](SECURITY.md), and [changelog](CHANGELOG.md)

The MIT License permits use in commercial and closed-source applications, provided the license and copyright notices are retained. See [LICENSE](LICENSE) for the full text. Thanks to Laravel and Spring for the ideas and infrastructure this integration builds on.
