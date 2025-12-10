package com.example.MrPot.service;

import com.example.MrPot.model.RagAnswer;
import com.example.MrPot.model.RagAnswerRequest;
import com.example.MrPot.model.RagQueryRequest;
import com.example.MrPot.model.RagRetrievalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class RagAnswerService {

    private final RagRetrievalService ragRetrievalService;
    private final RedisChatMemoryService chatMemoryService;
    private final Map<String, ChatClient> chatClients;

    private static final int DEFAULT_TOP_K = 3;
    private static final double DEFAULT_MIN_SCORE = 0.60;

    public RagAnswer answer(RagAnswerRequest request) {
        RagRetrievalResult retrieval = ragRetrievalService.retrieve(toQuery(request));
        ChatClient chatClient = resolveClient(request.resolveModel());

        RagAnswerRequest.ResolvedSession session = request.resolveSession();
        var history = chatMemoryService.loadHistory(session.id());
        String prompt = buildPrompt(request.question(), retrieval, chatMemoryService.renderHistory(history));

        var response = chatClient.prompt()
                .system("You are Mr Pot, a helpful assistant. Use the provided context and chat history to answer succinctly.")
                .user(prompt)
                .call();

        String answer = response.content();
        chatMemoryService.appendTurn(session.id(), request.question(), answer, session.temporary());
        return new RagAnswer(answer, retrieval.documents());
    }

    public Flux<String> streamAnswer(RagAnswerRequest request) {
        RagRetrievalResult retrieval = ragRetrievalService.retrieve(toQuery(request));
        ChatClient chatClient = resolveClient(request.resolveModel());

        RagAnswerRequest.ResolvedSession session = request.resolveSession();
        var history = chatMemoryService.loadHistory(session.id());
        String prompt = buildPrompt(request.question(), retrieval, chatMemoryService.renderHistory(history));

        AtomicReference<StringBuilder> aggregate = new AtomicReference<>(new StringBuilder());

        return chatClient.prompt()
                .system("You are Mr Pot, a helpful assistant. Use the provided context and chat history to answer succinctly.")
                .user(prompt)
                .stream()
                .content()
                .doOnNext(delta -> aggregate.get().append(delta))
                .doFinally(signalType -> chatMemoryService.appendTurn(
                        session.id(),
                        request.question(),
                        aggregate.get().toString(),
                        session.temporary()
                ));
    }

    private RagQueryRequest toQuery(RagAnswerRequest request) {
        return new RagQueryRequest(
                request.question(),
                request.resolveTopK(DEFAULT_TOP_K),
                request.resolveMinScore(DEFAULT_MIN_SCORE)
        );
    }

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

    private String buildPrompt(String question, RagRetrievalResult retrieval, String historyText) {
        StringBuilder sb = new StringBuilder();
        sb.append("Conversation History:\n").append(historyText).append("\n\n");
        sb.append("Retrieved Context:\n").append(retrieval.context()).append("\n\n");
        sb.append("User Question: ").append(question).append("\n");
        sb.append("Answer with clear and concise language. If context is insufficient, acknowledge the gap.");
        return sb.toString();
    }
}
