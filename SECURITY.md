# Security

The primary security guarantees are preventing precognitive requests from invoking the controller method, preserving normal authorization and CSRF behavior, rejecting unsupported handler signatures at startup, and enforcing request body, header, and response limits.

The current published release is `0.1.0`. There are no supported legacy release series. The tested version matrix is available in [README.md](README.md#verification-status).

If you discover a vulnerability, do not share valid credentials or real user data. If private vulnerability reporting is enabled for the GitHub repository, submit a private report through the Security tab. Otherwise, arrange a private communication channel with the maintainer before disclosing reproduction details publicly. Include exact versions, expected and actual behavior, and, where possible, a minimal test demonstrating that the controller method executes unexpectedly.

The library does not configure an authentication system, disable CSRF, or automatically enable CORS. Constraint, converter, and authorizer code runs with the application's privileges. Business rules, database constraints, and authorization must be enforced again on the final submission.
