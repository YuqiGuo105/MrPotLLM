package com.example.MrPot.service;

import com.example.MrPot.model.RagQueryRequest;
import com.example.MrPot.model.RagRetrievalResult;
import com.example.MrPot.model.ScoredDocument;
import com.example.MrPot.repository.KbDocumentVectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * RAG retrieval-only service:
 * - Takes user input (question)
 * - Generates embedding
 * - Queries the vector store
 * - Applies dynamic score filtering
 * - Builds LLM-ready context text
 *
 * This service does NOT call any chat/LLM APIs.
 * It is intended to be reused by various "tool services"
 * that need knowledge from the internal KB.
 */
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    // How many candidate documents to fetch from the vector store per query
    private static final int DEFAULT_TOP_K = 3;

    // Absolute score floor (to avoid returning very low-quality results)
    private static final double DEFAULT_MIN_SCORE = 0.20;

    // Margin relative to the top1 score: keep only documents with score >= (topScore - margin)
    private static final double TOP_SCORE_MARGIN = 0.10;

    private final EmbeddingModel embeddingModel;
    private final KbDocumentVectorRepository kbRepository;

    /**
     * Core retrieval method:
     * 1. Extract question
     * 2. Embed question
     * 3. Query vector store
     * 4. Dynamic filter by score
     * 5. Build LLM context
     */
    public RagRetrievalResult retrieve(RagQueryRequest request) {
        // 1. Get user question
        String question = request.question();

        // 2. Generate query embedding from the question
        float[] queryEmbedding = embeddingModel.embed(question);

        // 3. Resolve retrieval parameters
        int topK = request.resolveTopK(DEFAULT_TOP_K);
        double requestedMinScore = request.resolveMinScore(DEFAULT_MIN_SCORE);

        // 4. Query vector store for topK nearest documents (raw retrieved)
        List<ScoredDocument> retrieved = kbRepository.findNearest(queryEmbedding, topK);

        // 5. Dynamic filter by similarity score (relative to top1 + absolute floor)
        List<ScoredDocument> filtered = applyDynamicScoreFilter(retrieved, requestedMinScore);

        // 6. Build textual context for LLM consumption
        String context = buildContext(filtered);

        return new RagRetrievalResult(
                question,
                filtered,
                context
        );
    }

    /**
     * Dynamic threshold filtering logic:
     *  - If there are no retrieved documents → return an empty list.
     *  - topScore = score of the first (best) document.
     *  - dynamicThreshold = max(DEFAULT_MIN_SCORE, requestedMinScore, topScore - TOP_SCORE_MARGIN)
     *  - Keep only documents with score >= dynamicThreshold.
     *  - If everything is filtered out, keep at least the top1 document.
     */
    private List<ScoredDocument> applyDynamicScoreFilter(List<ScoredDocument> retrieved,
                                                         double requestedMinScore) {
        if (retrieved == null || retrieved.isEmpty()) {
            return List.of();
        }

        double topScore = retrieved.getFirst().score();

        double dynamicThreshold = Math.max(
                DEFAULT_MIN_SCORE,
                Math.max(requestedMinScore, topScore - TOP_SCORE_MARGIN)
        );

        List<ScoredDocument> filtered = retrieved.stream()
                .filter(doc -> doc.score() >= dynamicThreshold)
                .toList();

        // Safety: if the threshold is too high and filters out everything, keep at least top1
        if (filtered.isEmpty()) {
            return List.of(retrieved.getFirst());
        }

        return filtered;
    }

    /**
     * Convert a list of scored documents into a single context string
     * that can be injected into an LLM system prompt.
     */
    public String buildContext(List<ScoredDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            return "(no results)";
        }

        return docs.stream()
                .map(d -> {
                    var doc = d.document();
                    return "【docId=" + doc.getId()
                            + ", type=" + doc.getDocType()
                            + ", score=" + String.format(Locale.US, "%.3f", d.score())
                            + "】\n"
                            + doc.getContent();
                })
                .collect(Collectors.joining("\n\n"));
    }
}
