package com.example.MrPot.model;

public record ScoredDocument(
        KbDocument document,
        double score
) {
}
