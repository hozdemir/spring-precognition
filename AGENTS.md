# Repository instructions

This repository implements Spring Precognition as a Java 17 Maven multi-module library. The public API has no Servlet/Boot dependency; MVC logic and Boot 3/Jackson 2 versus Boot 4/Jackson 3 auto-configuration live in separate modules.

- Build with `mvn test`; `.tools/` cache users can run `./scripts/maven.sh -o test`.
- Run actual Security chains with `mvn -Psecurity-verification test`; socket tests with `mvn -Dtest=PrecognitionHttpIT test`.
- `./scripts/verify-external.sh` covers both consumers, official JS clients, Security, HTTP, browser, installed artifact contents and standalone JAR consumers. It requires network and loopback socket permission.
- `scripts/install-local.sh` installs into the standard `~/.m2/repository`. `scripts/maven.sh` uses workspace `.tools/m2` unless PRECOGNITION_MAVEN_REPO is set. Keep the distinction explicit in installation docs.
- Normal Spring binding, validation and security must be preserved. Precognitive requests never invoke the controller, and unselected constraints must not run.
- Never fix starter setup by expanding application component scanning or manually importing library configuration. Consumers must work through AutoConfiguration.imports.
- Unsupported opt-in signatures/security/custom MVC behavior fail at startup. Disabled mode retains its fail-closed guard and normal authorization policies.
- Keep `.tools/`, all `target/` and `node_modules/` out of Git. Do not commit generated reports or credentials.
- Record exact tested source/version combinations in VERIFICATION.md. Old single-module verification does not prove new package/artifact compatibility. Blocked/unexecuted checks must not be reported as passed.
- Example token auth and counters belong only in examples/common. Runtime JARs must not contain demo, verification or frontend code.
- MIT license copies in runtime module resources must match root LICENSE.

<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->
