# Installation

Spring Precognition is installed as an application dependency. It runs inside your application's JVM, without a separate process or service.

## Choosing a version

| Application | Starter | JSON adapter |
| --- | --- | --- |
| Boot 3.5 / Spring 6.2 | `precognition-spring-boot3-starter` | Jackson 2 |
| Boot 4.1 / Spring 7 | `precognition-spring-boot4-starter` | Jackson 3 |

The Java baseline is 17. Exact verified patch versions are recorded in [VERIFICATION.md](../VERIFICATION.md). Boot 2, WebFlux, and native/AOT are outside the supported scope. Keep your application's existing Spring Boot parent or BOM.

## Installing from source locally

From the repository root:

```sh
mvn install
```

This builds the Maven modules, runs the default consumer, startup, and MVC tests, and installs the artifacts in Maven's local repository. The default location is `~/.m2/repository`. If Maven is not installed on your PATH but this workspace contains a cached Maven distribution, use:

```sh
./scripts/install-local.sh
```

Example paths after installation:

```text
~/.m2/repository/dev/precognition/precognition-parent/0.1.0-SNAPSHOT/
~/.m2/repository/dev/precognition/precognition-core/0.1.0-SNAPSHOT/
~/.m2/repository/dev/precognition/precognition-spring-webmvc/0.1.0-SNAPSHOT/
~/.m2/repository/dev/precognition/precognition-spring-boot4-autoconfigure/0.1.0-SNAPSHOT/
~/.m2/repository/dev/precognition/precognition-spring-boot4-starter/0.1.0-SNAPSHOT/
```

Copying only the starter JAR is insufficient because the starter has transitive dependencies. Let Maven or Gradle resolve the complete dependency graph from the POMs.

## Maven consumers

Add the appropriate dependency to your application's `pom.xml`:

```xml
<dependency>
  <groupId>dev.precognition</groupId>
  <artifactId>precognition-spring-boot4-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

For Boot 3.5, use `precognition-spring-boot3-starter`. Do not add both starters. No additional repository declaration is required for artifacts installed in the standard local Maven repository. The library has not been published to Maven Central, so adding this dependency without installing the sources or making the artifacts available in another repository will fail resolution.

## Gradle consumers

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("dev.precognition:precognition-spring-boot4-starter:0.1.0-SNAPSHOT")
}
```

`mavenLocal()` reads artifacts installed in Maven's local repository. For shared team or CI use, publish the artifacts to a shared Maven repository and configure that repository in the consumer. See the [Gradle repository documentation](https://docs.gradle.org/current/userguide/declaring_repositories.html). This Gradle configuration is an example; this repository's build and test automation uses Maven.

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

For team distribution, make the artifacts available through a shared Maven repository. Maven Central publication is pending final namespace ownership and project metadata.

## Separate frontend origins and CORS

The examples' `ExampleCorsConfiguration` installs a restricted CORS filter only when `demo.cors-origin` is configured. It runs before authentication so that preflight and early 401 responses receive the required CORS headers too. The browser suites run the example frontend and Tomcat on different origins to check success, validation error, and unauthorized flows.

In your application, choose the allowed origins, authentication and CSRF headers, and exposed response headers. Configure credentials consistently if you use cookie authentication. The starter does not automatically enable CORS. See the [HTTP protocol](PROTOCOL.md) for details.

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| Starter artifact cannot be resolved | Were the artifacts installed in the same local repository, or published to a configured remote repository? |
| `javax.*` / `jakarta.*` or Jackson major version errors | Check for Boot 2 or the wrong starter |
| `ThemeResolver` or framework major version class errors | Boot 3 and Boot 4 dependencies may be mixed; inspect the dependency tree |
| MVC adapter conflict at startup | Check for `@EnableWebMvc`, an existing `WebMvcRegistrations`, or a custom handler adapter affecting an opted-in endpoint |
| 412 | The endpoint may not be opted in, or `precognition.enabled` may be false |
| 400 unknown field | Use the JSON wire name in the selection header |
| No request when validating an unchanged input | Check the form helper's optimization for unchanged values |
| 401/403 | Meet the application's existing authentication, authorization, and CSRF requirements |

`./scripts/maven.sh` defaults to the workspace's `.tools/m2` for repository verification. Normal Maven consumers usually use `~/.m2/repository`. Keep those locations distinct. Set `PRECOGNITION_MAVEN_REPO` explicitly to override the repository used by the helper scripts.
