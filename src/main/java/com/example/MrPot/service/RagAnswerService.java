package com.example.MrPot.service;

import com.example.MrPot.model.RagAnswer;
import com.example.MrPot.model.RagAnswerRequest;
import com.example.MrPot.model.RagQueryRequest;
import com.example.MrPot.model.RagRetrievalResult;
import com.example.MrPot.model.ThinkingEvent;
import com.example.MrPot.service.ChatLogService;
import com.example.MrPot.tools.ToolProfile;
import com.example.MrPot.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class RagAnswerService {

    private final RagRetrievalService ragRetrievalService;
    private final RedisChatMemoryService chatMemoryService;
    private final ChatLogService chatLogService;
    private final Map<String, ChatClient> chatClients;
    private final ToolRegistry toolRegistry;

    private static final int DEFAULT_TOP_K = 3;
    private static final double DEFAULT_MIN_SCORE = 0.60;

    /**
     * Non-streaming RAG answer:
     * - Retrieve related documents
     * - Build prompt with history + context
     * - Call LLM once
     * - Persist turn into Redis chat memory
     */
    public RagAnswer answer(RagAnswerRequest request) {
        RagRetrievalResult retrieval = ragRetrievalService.retrieve(toQuery(request));
        ChatClient chatClient = resolveClient(request.resolveModel());

        RagAnswerRequest.ResolvedSession session = request.resolveSession();
        var history = chatMemoryService.loadHistory(session.id());
        String prompt = buildPrompt(request.question(), retrieval, chatMemoryService.renderHistory(history));

        ToolProfile profile = request.resolveToolProfile(ToolProfile.BASIC_CHAT);
        List<String> toolBeanNames = toolRegistry.getFunctionBeanNamesForProfile(profile);

        var response = chatClient.prompt()
                .system("You are Mr Pot, a helpful assistant. Use the provided context and chat history to answer succinctly.")
                .user(prompt)
                .call();

        String answer = response.content();
        chatMemoryService.appendTurn(session.id(), request.question(), answer, session.temporary());
        chatLogService.recordChat(session.id(), request.resolveModel(), request.question(), prompt, answer, retrieval.documents());
        return new RagAnswer(answer, retrieval.documents());
    }

    /**
     * Streaming answer (plain text, no logic chain metadata).
     * - This is your original stream method, kept as-is for backward compatibility.
     */
    public Flux<String> streamAnswer(RagAnswerRequest request) {
        RagRetrievalResult retrieval = ragRetrievalService.retrieve(toQuery(request));
        ChatClient chatClient = resolveClient(request.resolveModel());

        RagAnswerRequest.ResolvedSession session = request.resolveSession();
        var history = chatMemoryService.loadHistory(session.id());
        String prompt = buildPrompt(request.question(), retrieval, chatMemoryService.renderHistory(history));

        AtomicReference<StringBuilder> aggregate = new AtomicReference<>(new StringBuilder());

        return chatClient.prompt()
                .system("You are Mr Pot. Answer succinctly in the user's language using the given context and history.")
                .user(prompt)
                .stream()
                .content()
                // Collect all deltas so we can persist the full answer at the end
                .doOnNext(delta -> aggregate.get().append(delta))
                .doFinally(signalType -> chatMemoryService.appendTurn(
                        session.id(),
                        request.question(),
                        aggregate.get().toString(),
                        session.temporary()
                ))
                .doFinally(signalType -> chatLogService.recordChat(
                        session.id(),
                        request.resolveModel(),
                        request.question(),
                        prompt,
                        aggregate.get().toString(),
                        retrieval.documents()
                ));
    }

    /**
     * Streaming answer WITH logic chain metadata, optimized for lower latency.
     *
     * Stages:
     *  - "start": request accepted, pipeline initialized
     *  - "redis": loaded previous conversation from Redis
     *  - "rag": searched knowledge base for related documents
     *  - "answer_delta": LLM token stream
     *  - "answer_final": final aggregated answer
     */
    public Flux<ThinkingEvent> streamAnswerWithLogic(RagAnswerRequest request) {
        // --- Resolve session and client up front (cheap operations) ---
        RagAnswerRequest.ResolvedSession session = request.resolveSession();
        ChatClient chatClient = resolveClient(request.resolveModel());

        // Per-subscription buffer for the aggregated answer text
        AtomicReference<StringBuilder> aggregate =
                new AtomicReference<>(new StringBuilder());

        // Keep prompt and retrieval for logging once the stream completes
        AtomicReference<String> promptRef = new AtomicReference<>("");
        AtomicReference<RagRetrievalResult> retrievalRef = new AtomicReference<>();

        // --- Async Redis history load ---
        // Run on boundedElastic to avoid blocking main threads
        Mono<List<RedisChatMemoryService.StoredMessage>> historyMono =
                Mono.fromCallable(() -> chatMemoryService.loadHistory(session.id()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .cache(); // Ensure only one actual Redis call per subscription

        // --- Async RAG retrieval ---
        // Also run on boundedElastic since embedding + DB are blocking IO
        Mono<RagRetrievalResult> retrievalMono =
                Mono.fromCallable(() -> ragRetrievalService.retrieve(toQuery(request)))
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnNext(retrievalRef::set)
                        .cache(); // Ensure only one actual retrieval per subscription

        // --- Stage 0: "start" -> fire immediately for ultra-low first-byte latency ---
        Flux<ThinkingEvent> startStep = Flux.just(
                new ThinkingEvent(
                        "start",
                        "Request received. Initializing thinking pipeline.",
                        Map.of("ts", System.currentTimeMillis())
                )
        );

        // --- Stage 1: "redis" -> emit once history is loaded ---
        Flux<ThinkingEvent> redisStep = historyMono.flatMapMany(history ->
                Flux.just(
                        new ThinkingEvent(
                                "redis",
                                "Combined with previous conversation; some context from Redis chat history.",
                                summarizeHistory(history)
                        )
                )
        );

        // --- Stage 2: "rag" -> emit once RAG retrieval is done ---
        Flux<ThinkingEvent> ragStep = retrievalMono.flatMapMany(retrieval ->
                Flux.just(
                        new ThinkingEvent(
                                "rag",
                                "Searching knowledge base for related content.",
                                summarizeRetrieval(retrieval)
                        )
                )
        );

        // --- Stage 3: "answer_delta" -> LLM streaming token output ---
        Flux<ThinkingEvent> answerDeltaStep =
                Mono.zip(historyMono, retrievalMono)
                        .flatMapMany(tuple -> {
                            var history = tuple.getT1();
                            var retrieval = tuple.getT2();

                            String historyText = chatMemoryService.renderHistory(history);
                            String prompt = buildPrompt(
                                    request.question(),
                                    retrieval,
                                    historyText
                            );
                            promptRef.set(prompt);

                            return chatClient.prompt()
                                    .system("You are Mr Pot, a helpful assistant. " +
                                            "Answer succinctly in the user's language, " +
                                            "using only the provided context and chat history.")
                                    .user(prompt)
                                    .stream()
                                    .content()
                                    .map(delta -> {
                                        // Aggregate all deltas into a single final answer
                                        aggregate.get().append(delta);
                                        return new ThinkingEvent(
                                                "answer_delta",
                                                "Generating answer.",
                                                delta
                                        );
                                    });
                        })
                        .doFinally(signalType -> {
                            // Persist the full answer in Redis chat memory once streaming finishes
                            chatMemoryService.appendTurn(
                                    session.id(),
                                    request.question(),
                                    aggregate.get().toString(),
                                    session.temporary()
                            );
                            chatLogService.recordChat(
                                    session.id(),
                                    request.resolveModel(),
                                    request.question(),
                                    promptRef.get(),
                                    aggregate.get().toString(),
                                    Optional.ofNullable(retrievalRef.get())
                                            .map(RagRetrievalResult::documents)
                                            .orElse(List.of())
                            );
                        });

        // --- Stage 4: "answer_final" -> emit the complete answer at the end ---
        Flux<ThinkingEvent> finalStep = Flux.defer(() ->
                Flux.just(
                        new ThinkingEvent(
                                "answer_final",
                                "Finalized answer.",
                                aggregate.get().toString()
                        )
                )
        );

        // Final order:
        //  start → redis → rag → answer_delta* → answer_final
        return Flux.concat(startStep, redisStep, ragStep, answerDeltaStep, finalStep);
    }

    /**
     * Convert a high-level RAG answer request into a retrieval-only query.
     */
    private RagQueryRequest toQuery(RagAnswerRequest request) {
        return new RagQueryRequest(
                request.question(),
                request.resolveTopK(DEFAULT_TOP_K),
                request.resolveMinScore(DEFAULT_MIN_SCORE)
        );
    }

    /**
     * Resolve ChatClient bean based on requested model identifier.
     * Supported lookup keys:
     *  - "<model>ChatClient"
     *  - "<model>"
     * Fallback:
     *  - default model "ChatClient"
     *  - any available ChatClient if nothing matches
     */
    private ChatClient resolveClient(String model) {
        String key = Optional.ofNullable(model)
                .map(String::toLowerCase)
                .orElse(RagAnswerRequest.DEFAULT_MODEL);
        if (!chatClients.isEmpty()) {
            if (chatClients.containsKey(key + "ChatClient")) {
                return chatClients.get(key + "ChatClient");
            }
            if (chatClients.containsKey(key)) {
                return chatClients.get(key);
            }
        }
        ChatClient fallback = chatClients.get(RagAnswerRequest.DEFAULT_MODEL + "ChatClient");
        if (fallback != null) {
            return fallback;
        }
        return chatClients.values().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No ChatClient beans are available"));
    }

    /**
     * Build the combined prompt:
     *  - textual conversation history
     *  - retrieved KB context
     *  - user question
     */
    private String buildPrompt(String question, RagRetrievalResult retrieval, String historyText) {
        StringBuilder sb = new StringBuilder();
        sb.append("Conversation History:\n").append(historyText).append("\n\n");
        sb.append("Retrieved Context:\n").append(retrieval.context()).append("\n\n");
        sb.append("User Question: ").append(question).append("\n");
        sb.append("Answer with clear and concise. You can infer based on info.");
        return sb.toString();
    }

    /**
     * Summarize chat history for UI / debug payload.
     * Only keeps the last N messages to avoid huge payloads.
     */
    private List<Map<String, Object>> summarizeHistory(List<RedisChatMemoryService.StoredMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        int maxMessages = 6; // e.g. last 3 turns (user+assistant)
        int startIdx = Math.max(0, history.size() - maxMessages);

        return history.subList(startIdx, history.size()).stream()
                .map(m -> Map.<String, Object>of(
                        "role", m.role(),
                        "content", m.content()
                ))
                .toList();
    }

    /**
     * Summarize retrieval result for UI / debug payload.
     * Sends basic metadata and a short preview of each matched document.
     */
    private List<Map<String, Object>> summarizeRetrieval(RagRetrievalResult retrieval) {
        if (retrieval == null || retrieval.documents() == null || retrieval.documents().isEmpty()) {
            return List.of();
        }

        return retrieval.documents().stream()
                .map(sd -> {
                    var doc = sd.document();
                    String content = doc.getContent();
                    String preview;
                    if (content == null) {
                        preview = "";
                    } else if (content.length() > 200) {
                        preview = content.substring(0, 200) + "...";
                    } else {
                        preview = content;
                    }

                    return Map.<String, Object>of(
                            "id", doc.getId(),
                            "type", doc.getDocType(),
                            "score", sd.score(),
                            "preview", preview
                    );
                })
                .toList();
    }
}
