package com.example.MrPot.model;

/**
 * A single "thinking" step event for streaming logic chain to the client.
 *
 * stage   - pipeline stage name, e.g. "redis", "rag", "answer_delta", "answer_final"
 * message - human-readable description of what this step means
 * payload - arbitrary payload for UI, e.g.:
 *           - List<Map<...>> for history or retrieval summaries
 *           - String for answer token delta
 *           - String for final full answer
 */
public record ThinkingEvent(
        String stage,
        String message,
        Object payload
) {
}
