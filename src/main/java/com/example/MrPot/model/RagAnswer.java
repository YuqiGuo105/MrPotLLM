package com.example.MrPot.model;

import java.util.List;

public record RagAnswer(
        String answer,
        List<ScoredDocument> supportingDocs
) {
}
