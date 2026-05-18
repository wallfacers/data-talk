// Centralized thresholds for the batch image attachments path. base64 inflates
// the original bytes by ~33%, so these limits guard the OpenCode promptAsync
// request body and the single SSE frame that carries it. Tune after observing
// real OpenCode behavior in E2E.

export const IMAGE_PAYLOAD_WARN_BYTES = 3 * 1024 * 1024
export const IMAGE_PAYLOAD_HARD_LIMIT_BYTES = 5 * 1024 * 1024

// Maximum time the composer waits at send-time for an in-flight image
// prefetch to resolve before falling back to the legacy read_file path. Kept
// large enough to absorb typical localhost GET latency for a freshly-uploaded
// image; if exceeded the message still goes out, just without the inline
// FilePart.
export const IMAGE_DATA_URI_FETCH_TIMEOUT_MS = 5000
