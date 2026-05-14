## ADDED Requirements

### Requirement: Composer streaming flag follows turn lifecycle, not request lifecycle

The system SHALL maintain `streamingBySession[sessionId]` for the prompt-composer's send/stop button as a state machine bound to the AI **turn** lifecycle, decoupled from any single HTTP request's lifecycle. The flag SHALL be set to `true` at user-initiated turn start and cleared **only** by one of: a turn-complete SSE event (`session.idle`, `session.status=idle`, `session.error`), an explicit user abort, or a request failure that occurred **before** the SSE stream was opened.

Aborted or interrupted POST send-message requests (including Ctrl+R navigation aborts) where the SSE stream **was** opened SHALL NOT clear the flag, because the backend turn may continue and a separate long-lived GET /subscribe stream will deliver the eventual turn-complete event.

#### Scenario: Mid-stream Ctrl+R refresh preserves streaming flag

- **GIVEN** a session with an in-flight POST send_message that has emitted at least the `connected` SSE frame
- **WHEN** the user triggers a page reload (Ctrl+R) and the underlying `fetch` is aborted by the navigation
- **THEN** `streamingBySession[sessionId]` SHALL remain `true` in sessionStorage at the moment of page unload
- **AND** after the page reloads, the composer SHALL render the stop (`Loader2Icon`) button, not the send (`ArrowUpIcon`) button

#### Scenario: Pre-stream POST failure clears streaming flag

- **GIVEN** a session where the user has just sent a message and `setStreaming(sessionId, true)` has been called
- **WHEN** the POST send_message request fails before any SSE frame (including `connected`) is received — e.g., DNS failure, 5xx HTTP response, TLS error, server unreachable
- **THEN** `streamingBySession[sessionId]` SHALL be set back to `false` synchronously inside the request's catch handler
- **AND** the composer SHALL render the send button so the user can retry

#### Scenario: Natural turn completion via session.idle clears streaming flag

- **GIVEN** a session with an in-flight turn whose POST SSE stream has delivered events including at least one `message.part.delta`
- **WHEN** the backend emits a `session.idle` or `session.status=idle` event
- **THEN** `buildEventSink` SHALL call `setStreaming(sessionId, false)` via the existing turn-done handler
- **AND** the composer SHALL render the send button

#### Scenario: User-initiated abort clears streaming flag

- **GIVEN** an in-flight turn with `streamingBySession[sessionId] == true`
- **WHEN** the user presses the stop button and `useChannel.abort()` resolves
- **THEN** `streamingBySession[sessionId]` SHALL be cleared either via `abort()`'s explicit `setStreaming(false)` call (when backend reports `aborted=false`) or via the subsequent `session.idle` SSE event (when backend successfully aborts the turn)
- **AND** the composer SHALL render the send button

#### Scenario: retryPendingUser obeys the same lifecycle contract

- **GIVEN** a failed pending user message and a user-initiated retry
- **WHEN** `useChannel.retryPendingUser()` is called and follows the same POST → SSE → completion flow
- **THEN** the streaming flag clearance rules from the scenarios above SHALL apply identically

### Requirement: Streaming flag clearance is idempotent across sinks

Both the long-lived GET /subscribe sink and the per-turn POST send-message sink are connected to the same `SessionBus` event stream. The system SHALL ensure that `setStreaming(sessionId, false)` may be called multiple times from either sink without producing observable side effects beyond the first call.

#### Scenario: Multiple idle events from concurrent sinks do not regress UI

- **GIVEN** both a GET subscribe sink and a POST send-message sink active for the same session
- **WHEN** a `session.idle` event is fanned out to both sinks by `SessionBus`, causing two `setStreaming(sessionId, false)` calls
- **THEN** `streamingBySession[sessionId]` SHALL be `false` exactly once, with no toggle or flicker
- **AND** no additional side effects (e.g., duplicate toasts, duplicate analytics events) SHALL be triggered

### Requirement: Stream-opened signal uses the connected SSE frame

The system SHALL recognize the `connected` SSE event (emitted by backend `ChannelController` via `bus.publish(new DtEvent.Connected(...))` immediately after subscribe success) as the boundary between "request opened a stream" and "request never opened a stream."

#### Scenario: connected frame marks the request as stream-opened

- **GIVEN** a POST send_message request whose sink receives an event with `event === 'connected'`
- **WHEN** the request later fails (e.g., reader.read() rejects due to navigation abort)
- **THEN** the failure handler SHALL classify the request as "stream-opened" and SHALL NOT call `setStreaming(sessionId, false)`

#### Scenario: connected frame absent means stream never opened

- **GIVEN** a POST send_message request whose sink has not yet received any `connected` event
- **WHEN** the request fails (HTTP error, network error)
- **THEN** the failure handler SHALL classify the request as "pre-stream failure" and SHALL call `setStreaming(sessionId, false)` synchronously
