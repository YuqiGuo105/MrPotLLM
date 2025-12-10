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
 * - Applies basic filtering
 * - Builds LLM-ready context text
 *
 * This service does NOT call any chat/LLM APIs.
 * It is intended to be reused by various "tool services"
 * that need knowledge from the internal KB.
 */
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private static final int DEFAULT_TOP_K = 3;
    private static final double DEFAULT_MIN_SCORE = 0.60;

    private final EmbeddingModel embeddingModel;
    private final KbDocumentVectorRepository kbRepository;

    /**
     * Core retrieval method:
     * 1. Extract question
     * 2. Embed question
     * 3. Query vector store
     * 4. Filter by minScore
     * 5. Build LLM context
     *
     * @param request RAG query request from client
     * @return retrieval result including:
     *         - original question
     *         - filtered documents
     *         - formatted context string for LLM
     */
    public RagRetrievalResult retrieve(RagQueryRequest request) {
        // 1. Get user question
        String question = request.question();

        // 2. Generate query embedding from the question
        float[] queryEmbedding = embeddingModel.embed(question);

        // 3. Resolve retrieval parameters
        int topK = request.resolveTopK(DEFAULT_TOP_K);
        double minScore = request.resolveMinScore(DEFAULT_MIN_SCORE);

        // 4. Query vector store for topK nearest documents
        List<ScoredDocument> retrieved = kbRepository.findNearest(queryEmbedding, topK);

        // 5. Filter by similarity score to remove low-quality matches
        List<ScoredDocument> filtered = retrieved.stream()
                .filter(doc -> doc.score() >= minScore)
                .toList();

        // 6. Build textual context for LLM consumption
        String context = buildContext(filtered);

        return new RagRetrievalResult(
                question,
                filtered,
                context
        );
    }

    /**
     * Convert a list of scored documents into a single context string
     * that can be injected into an LLM system prompt.
     *
     * Example format:
     *   【docId=..., type=..., score=0.873】
     *   document content...
     *
     *   【docId=..., type=..., score=0.751】
     *   document content...
     *
     * @param docs scored documents from vector store
     * @return concatenated context string
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
