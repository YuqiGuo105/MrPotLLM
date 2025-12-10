package com.example.MrPot.model;

import java.util.List;

/**
 * Pure retrieval result for RAG:
 * - question: original user question
 * - documents: filtered scored documents from vector store
 * - context: formatted context string for LLM prompts
 */
public record RagRetrievalResult(
        String question,
        List<ScoredDocument> documents,
        String context
) {}

