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
     * 简单模式：
     *  - 前端只传 question；topK/minScore 用默认值
     *  请求示例：
     *    GET /api/rag/retrieve?q=xxx
     */
    @GetMapping("/retrieve")
    public RagRetrievalResult retrieveByQueryParam(
            @RequestParam("q") String question
    ) {
        RagQueryRequest req = new RagQueryRequest(
                question,
                null,   // 使用 RagRetrievalService 默认 topK
                null    // 使用 RagRetrievalService 默认 minScore
        );
        return ragRetrievalService.retrieve(req);
    }

    /**
     * 高级模式：
     *  - 前端可以控制 topK / minScore
     *  请求示例：
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
