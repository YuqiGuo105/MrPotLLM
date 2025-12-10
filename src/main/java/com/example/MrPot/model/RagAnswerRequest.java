package com.example.MrPot.model;

/**
 * Request payload for generating an answer with RAG context.
 *
 * @param question   user question
 * @param sessionId  chat session id for memory separation
 * @param topK       optional override for retrieval topK
 * @param minScore   optional override for retrieval minScore
 * @param model      optional model name hint (e.g. "deepseek", "openai")
 */
public record RagAnswerRequest(
        String question,
        String sessionId,
        Integer topK,
        Double minScore,
        String model
) {

    public static final String DEFAULT_MODEL = "deepseek";

    public int resolveTopK(int defaultValue) {
        return topK == null || topK <= 0 ? defaultValue : topK;
    }

    public double resolveMinScore(double defaultValue) {
        return minScore == null ? defaultValue : minScore;
    }

    public String resolveModel() {
        return (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
    }

    public ResolvedSession resolveSession() {
        boolean temporary = sessionId == null || sessionId.isBlank();
        String resolvedId = temporary ? "temp-" + java.util.UUID.randomUUID() : sessionId;
        return new ResolvedSession(resolvedId, temporary);
    }

    public record ResolvedSession(String id, boolean temporary) { }
}
