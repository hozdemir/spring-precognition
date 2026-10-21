# Contributing

Java 17+ and Maven 3.6.3+ are required. Frontend verification uses Node 20+. The main development workflow is:

```sh
mvn test
mvn -Psecurity-verification test
./scripts/verify-external.sh
```

If a cached Maven installation is available under `.tools/`, `./scripts/maven.sh` runs the same build. This launcher is not the standard Maven Wrapper. Set `PRECOGNITION_MAVEN_REPO` to choose the local repository path; the default is `.tools/m2` in the workspace. `scripts/install-local.sh` installs into the standard `~/.m2/repository` used by consumer projects.

The example applications do not include library packages in their component scans. Integration must work through the starter's auto-configuration metadata. Do not add `@Import(PrecognitionConfiguration.class)` to an example to make tests pass; this hides setup errors.

A bug fix should include a test that reproduces the incorrect behavior. Precognitive requests must never invoke the controller method. Use invocation counters to verify that unselected constraints do not execute. An HTTP 204 response alone is insufficient evidence. Preserve validation, authorization, and successful execution for normal submissions.

HTTP and browser tests open real loopback ports. If the environment prevents opening ports or downloading dependencies, do not report those checks as passed. Surefire and Playwright reports are generated artifacts and must not be committed to Git.

The public API lives in `precognition-core`. MVC, JSON, and binder components are not stable extension APIs. Jackson 2 and Jackson 3 imports must remain in their respective Boot adapters. The corresponding Boot parent or BOM manages Spring and validation provider versions. Do not add both starters to the same consumer project.

Contributions are provided under the [MIT License](LICENSE). Before a public release, run the full verification pipeline and check the version metadata.
