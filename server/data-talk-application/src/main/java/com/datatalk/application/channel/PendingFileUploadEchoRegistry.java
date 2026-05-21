package com.datatalk.application.channel;

import com.datatalk.domain.part.FileUploadPart;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds {@link FileUploadPart}s that DataTalk needs to echo back to the
 * frontend as {@code message.part.created} events. The OpenCode protocol
 * cannot carry these parts (strict Zod validation rejects DataTalk-specific
 * fields), so {@code ChannelService} downgrades them to text before
 * forwarding — and we re-publish the original file_upload parts locally
 * once the user {@code message.updated} event echoes back with a real
 * OpenCode messageID.
 *
 * <p>Per-session FIFO: each {@code sendMessage} call enqueues one batch;
 * the next user {@code MessageCreated} drains the head of the queue.</p>
 */
@Component
public class PendingFileUploadEchoRegistry {

    private final Map<String, Deque<List<FileUploadPart>>> queues = new ConcurrentHashMap<>();

    public void enqueue(String dataTalkSessionId, List<FileUploadPart> parts) {
        if (dataTalkSessionId == null || parts == null || parts.isEmpty()) {
            return;
        }
        queues.computeIfAbsent(dataTalkSessionId, k -> new ArrayDeque<>())
            .add(List.copyOf(parts));
    }

    public List<FileUploadPart> drainNext(String dataTalkSessionId) {
        if (dataTalkSessionId == null) {
            return List.of();
        }
        Deque<List<FileUploadPart>> q = queues.get(dataTalkSessionId);
        if (q == null) {
            return List.of();
        }
        synchronized (q) {
            List<FileUploadPart> head = q.pollFirst();
            if (q.isEmpty()) {
                queues.remove(dataTalkSessionId, q);
            }
            return head == null ? List.of() : head;
        }
    }

    public void forgetSession(String dataTalkSessionId) {
        if (dataTalkSessionId != null) {
            queues.remove(dataTalkSessionId);
        }
    }
}
