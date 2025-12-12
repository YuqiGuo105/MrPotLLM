package com.example.MrPot.controller;

import com.example.MrPot.model.RagAnswer;
import com.example.MrPot.model.RagAnswerRequest;
import com.example.MrPot.model.ThinkingEvent;
import com.example.MrPot.service.RagAnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
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
        // 0L means no timeout (you can set a specific timeout instead if desired)
        SseEmitter emitter = new SseEmitter(0L);

        // RAG + LLM streaming with low-latency thinking stages
        // Stages: start / redis / rag / answer_delta / answer_final
        Flux<ThinkingEvent> stream = ragAnswerService.streamAnswerWithLogic(request);

        // Subscribe to the thinking stream and bridge it into SSE events
        Disposable subscription = stream.subscribe(
                event -> {
                    try {
                        // Use stage as SSE event name so frontend can handle each stage separately
                        emitter.send(
                                SseEmitter.event()
                                        .name(event.stage())
                                        .data(event)   // ThinkingEvent will be serialized as JSON
                        );
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                // On error
                emitter::completeWithError,
                // On completion
                emitter::complete
        );

        // Ensure we clean up the subscription when the SSE connection ends
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(subscription::dispose);
        emitter.onError(t -> subscription.dispose());

        return emitter;
    }
}
