package com.example.MrPot.controller;

import com.example.MrPot.model.RagAnswer;
import com.example.MrPot.model.RagAnswerRequest;
import com.example.MrPot.service.RagAnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagAnswerController {

    private final RagAnswerService ragAnswerService;

    @PostMapping("/answer")
    public RagAnswer answer(@RequestBody RagAnswerRequest request) {
        return ragAnswerService.answer(request);
    }

    @PostMapping(value = "/answer/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnswer(@RequestBody RagAnswerRequest request) {
        SseEmitter emitter = new SseEmitter();
        Flux<String> stream = ragAnswerService.streamAnswer(request);

        stream.subscribe(
                chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                emitter::completeWithError,
                emitter::complete
        );

        return emitter;
    }
}
