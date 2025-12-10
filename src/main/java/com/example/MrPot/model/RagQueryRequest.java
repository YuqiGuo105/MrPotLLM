package com.example.MrPot.model;

public record RagQueryRequest(
        String question,
        Integer topK,
        Double minScore
) {
    public int resolveTopK(int defaultValue) {
        return topK == null || topK <= 0 ? defaultValue : topK;
    }

    public double resolveMinScore(double defaultValue) {
        return minScore == null ? defaultValue : minScore;
    }
}
