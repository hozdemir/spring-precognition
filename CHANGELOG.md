# Changelog

## Unreleased — 0.1.0-SNAPSHOT

- Set the Maven group ID to `io.github.hozdemir` while retaining the `dev.precognition.spring` Java API package.
- Added project and SCM metadata, source and Javadoc artifacts, and an opt-in signed Central publishing profile with uploads disabled by default.
- Moved the public annotation, validation modes, request context, and authorization SPI into a separate core module.
- Separated the Spring MVC implementation from the Boot 3/Jackson 2 and Boot 4/Jackson 3 auto-configuration modules.
- Added platform-specific dependency starters and example applications that consume them from separate packages.
- Added startup compatibility checks for endpoints, models, security, MVC adapters, validators, and JSON converters.
- Added FULL and SELECTIVE validation, ordinary DTOs and scalar types, JSON wire names and naming strategies, and bounded nested graph support.
- Implemented shared authorizers, a request context accessor, a guard for disabled mode, normal submission error policies, and resource limits.
- Added optional ObservationRegistry integration with low-cardinality scope and status tags.
- Added official React/Vue client verification, installed-JAR consumers, CI configuration, installation documentation, and the MIT License.
- Shared header parsing between the ERROR dispatch and missing-filter guards to prevent malformed precognitive requests from reaching the normal controller action.
- Preserved existing WebMvcRegistrations beans and rejected custom adapters incompatible with opted-in endpoints at startup.
- Included trailing content in the size limit for JSON bodies of unknown length, checking the complete body after endpoint authorization and before binding.
- Aligned authentication tokens between the browser examples and backend, and added dispatch, MVC registration, and body limit regression tests for both platforms.
- Supported reactor-wide Surefire test selection in Boot 4 library modules while ensuring both example applications fail when a requested test is missing.

This version has not been published to Maven Central. Test results and verification limits are documented in [README.md](README.md#verification-status).
