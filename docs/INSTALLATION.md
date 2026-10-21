# Installation

Spring Precognition is installed as an application dependency. It runs inside your application's JVM, without a separate process or service.

Version `0.1.0` is available from Maven Central. Add one of the dependencies below directly to your application; a source checkout or local installation is optional.

## Choosing a version

| Application | Starter | JSON adapter |
| --- | --- | --- |
| Boot 3.5 / Spring 6.2 | `precognition-spring-boot3-starter` | Jackson 2 |
| Boot 4.1 / Spring 7 | `precognition-spring-boot4-starter` | Jackson 3 |

The Java baseline is 17. Exact verified patch versions are listed in the README's [verification status](../README.md#verification-status). Boot 2, WebFlux, and native/AOT are outside the supported scope. Keep your application's existing Spring Boot parent or BOM.

## Maven consumers

Add the appropriate dependency to your application's `pom.xml`:

```xml
<dependency>
  <groupId>io.github.hozdemir</groupId>
  <artifactId>precognition-spring-boot4-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

For Boot 3.5, use `precognition-spring-boot3-starter`. Do not add both starters. Maven uses Central by default; no additional repository declaration is required. If your organization routes dependencies through a repository manager, ensure its Central proxy is enabled.

Copying only the starter JAR is insufficient because the starter has transitive dependencies. Let Maven or Gradle resolve the complete dependency graph from the POMs.

## Gradle consumers

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.hozdemir:precognition-spring-boot4-starter:0.1.0")
}
```

See the [Gradle repository documentation](https://docs.gradle.org/current/userguide/declaring_repositories.html) for repository configuration. This Gradle configuration is an example; this repository's build and test automation uses Maven.

## Optional: installing from source locally

For changes to the library itself, run from the repository root with Java 17+ and Maven 3.6.3+:

```sh
mvn install
```

This builds the Maven modules, runs the default consumer, startup, and MVC tests, and installs the artifacts in Maven's local repository. The default location is `~/.m2/repository`. If Maven is not installed on your PATH but this workspace contains a cached Maven distribution, use:

```sh
./scripts/install-local.sh
```

Artifacts are installed under `~/.m2/repository/io/github/hozdemir/`. Maven consumers can resolve these local builds directly. To use them in a Gradle consumer, add `mavenLocal()` before `mavenCentral()` for local development.

The repository verification launcher `scripts/maven.sh` instead defaults to `.tools/m2`. Set `PRECOGNITION_MAVEN_REPO` to override the repository used by the helper scripts.

## Enabling an endpoint

Spring Boot's auto-configuration metadata installs the required beans. No `@ComponentScan("dev.precognition")`, manual configuration import, or additional `@Enable...` annotation is needed. Add `dev.precognition.spring.Precognitive` to the endpoint method and `@Valid` or Default-group `@Validated` to its body parameter.

The endpoint must have exactly one required body. Additional built-in scalar `@PathVariable`, `@RequestParam`, `@RequestHeader`, and Principal parameters are supported. Custom handler adapters, argument resolvers, MVC validators, body converters, or binders can change binding and validation behavior, so the startup verifier checks compatibility. Do not enable global bean overriding to hide a conflict; adapt the endpoint or configuration to the supported contract.

## Running the examples

```sh
./scripts/run-example.sh boot4
./scripts/run-example.sh boot3 -Dspring-boot.run.arguments=--server.port=8081
```

These commands use the same local Maven repository as `scripts/install-local.sh`. With a standard Maven installation, the equivalent Boot 4 command is:

```sh
mvn -f examples/boot4/pom.xml spring-boot:run
```

The example filter requires `Authorization: Bearer demo-token` on its protected routes. That filter, `/demo/counters`, Probe, and the demonstration validator are not included in the starter JARs.

## Production deployment

Package and deploy the application with its Spring Precognition dependency using your existing process. For example, your Spring Boot application's executable JAR or container image includes the required library dependencies. The target server does not need a clone of this repository, a separate source build, or a Precognition service.

The published Maven namespace is `io.github.hozdemir`; Java imports use `dev.precognition.spring`. Consumer builds resolve the release from Maven Central or your organization's Central proxy.

## Separate frontend origins and CORS

The examples' `ExampleCorsConfiguration` installs a restricted CORS filter only when `demo.cors-origin` is configured. It runs before authentication so that preflight and early 401 responses receive the required CORS headers too. The browser suites run the example frontend and Tomcat on different origins to check success, validation error, and unauthorized flows.

In your application, choose the allowed origins, authentication and CSRF headers, and exposed response headers. Configure credentials consistently if you use cookie authentication. The starter does not automatically enable CORS. See the [HTTP protocol](PROTOCOL.md) for details.

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| Starter artifact cannot be resolved | Check the group ID, version, Central access, Maven offline mode, and any repository mirror or proxy settings |
| `javax.*` / `jakarta.*` or Jackson major version errors | Check for Boot 2 or the wrong starter |
| `ThemeResolver` or framework major version class errors | Boot 3 and Boot 4 dependencies may be mixed; inspect the dependency tree |
| MVC adapter conflict at startup | Check for `@EnableWebMvc`, an existing `WebMvcRegistrations`, or a custom handler adapter affecting an opted-in endpoint |
| 412 | The endpoint may not be opted in, or `precognition.enabled` may be false |
| 400 unknown field | Use the JSON wire name in the selection header |
| No request when validating an unchanged input | Check the form helper's optimization for unchanged values |
| 401/403 | Meet the application's existing authentication, authorization, and CSRF requirements |

`./scripts/maven.sh` defaults to the workspace's `.tools/m2` for repository verification. Normal Maven consumers usually use `~/.m2/repository`. Keep those locations distinct. Set `PRECOGNITION_MAVEN_REPO` explicitly to override the repository used by the helper scripts.
