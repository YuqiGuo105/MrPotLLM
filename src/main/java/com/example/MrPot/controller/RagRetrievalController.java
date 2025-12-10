package com.example.MrPot.controller;

import com.example.MrPot.model.RagQueryRequest;
import com.example.MrPot.model.RagRetrievalResult;
import com.example.MrPot.service.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagRetrievalController {

    private final RagRetrievalService ragRetrievalService;

    /**
     * Simple mode:
     *  - Frontend only sends the question; topK/minScore use default values.
     *
     *  Example request:
     *    GET /api/rag/retrieve?q=xxx
     */
    @GetMapping("/retrieve")
    public RagRetrievalResult retrieveByQueryParam(
            @RequestParam("q") String question
    ) {
        RagQueryRequest req = new RagQueryRequest(
                question,
                null,   // Use RagRetrievalService default topK
                null    // Use RagRetrievalService default minScore
        );
        return ragRetrievalService.retrieve(req);
    }

    /**
     * Advanced mode:
     *  - Frontend can control topK / minScore.
     *
     *  Example request:
     *    POST /api/rag/retrieve
     *    {
     *      "question": "xxx",
     *      "topK": 8,
     *      "minScore": 0.65
     *    }
     */
    @PostMapping("/retrieve")
    public RagRetrievalResult retrieveByBody(
            @RequestBody RagQueryRequest request
    ) {
        return ragRetrievalService.retrieve(request);
    }
}
