# HTTP protocol

An opted-in endpoint must explicitly use POST, PUT, or PATCH. Authentication, URL authorization, and CSRF requirements are the same as for a normal request.

| Header | Meaning |
| --- | --- |
| `Precognition: true` | Validate input without invoking the controller |
| `Precognition: false`, or no signal header | Normal Spring request processing |
| `Precognition-Validate-Only: email,address.city` | Select registered JSON wire fields |
| No selection header | Full validation |
| Present but empty selection header | Bind and check the supported body, run no constraints, and return 204 on success |

The signal must be exactly `true` or `false` after surrounding ASCII whitespace is trimmed. Duplicate signal or selection headers, an empty signal, an orphan selection, and empty tokens in a comma-separated selection are rejected. Header input is never used as an expression or unrestricted reflection path.

## Responses

Success returns 204 with an empty body, `Precognition: true`, `Precognition-Success: true`, and `Cache-Control: no-store`. Validation failure returns 422 with a JSON `message/errors` envelope. Each field's error value is an array of strings. `_form` is the global error key. Messages come from the request locale and the application's validation provider configuration.

| Status | Code or behavior |
| --- | --- |
| 400 | `INVALID_PRECOGNITION_HEADER`, `ORPHAN_SELECTION_HEADER`, `INVALID_FIELD_SELECTION` |
| 400 | `UNKNOWN_VALIDATION_FIELD`, `SELECTION_NOT_SUPPORTED` |
| 400 | `INVALID_REQUEST_BODY`, `INVALID_REQUEST_ARGUMENT`, `PRECOGNITION_UNSUPPORTED_DISPATCH` |
| 401/403 | Preserve the application's early security response; add protocol recognition headers for a valid signal |
| 403 | Authorizer denial: `PRECOGNITION_ACCESS_DENIED` |
| 406 | `PRECOGNITION_NOT_ACCEPTABLE` |
| 412 | `PRECOGNITION_NOT_ENABLED`, `PRECOGNITION_DISABLED`; no success or protocol acceptance header |
| 413 | `PRECOGNITION_PAYLOAD_TOO_LARGE` |
| 415 | `PRECOGNITION_UNSUPPORTED_MEDIA_TYPE` |
| 431 | `PRECOGNITION_HEADERS_TOO_LARGE` |
| 500 | `PRECOGNITION_CONFIGURATION_ERROR`, `PRECOGNITION_INTERNAL_ERROR` |

Existing Vary tokens are preserved and deduplicated without regard to case. `Vary: *` is left unchanged. Normal responses from opted-in endpoints also vary on the two protocol headers. Normal responses do not receive a precognitive success header.

## Binding and resource limits

JSON deserialization includes unselected fields. A type conversion error in an unselected field can therefore fail the request. Compressed request bodies are unsupported. Exceeding a resource limit never silently reduces the fields or messages being validated.

The body limit includes the complete raw body, including trailing whitespace and chunked transfers. Accepted bodies are checked after shared authorization and before argument binding, independently of how much of the body the converter consumes.

## CORS

CORS is application policy. For a separate frontend origin, allow the request headers `Authorization`, `Content-Type`, `Precognition`, `Precognition-Validate-Only`, and any required CSRF headers. Expose the response headers `Precognition` and `Precognition-Success` to the browser. Preflight and early authentication responses must pass through the CORS filter too.

The examples configure a restricted CORS filter before authentication when `demo.cors-origin` is supplied. The starter does not automatically allow origins.
