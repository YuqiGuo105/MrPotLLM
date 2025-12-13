package com.example.MrPot.service;

import com.example.MrPot.model.ChatLog;
import com.example.MrPot.model.ScoredDocument;
import com.example.MrPot.repository.ChatLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatLogService {

    private static final Logger log = LoggerFactory.getLogger(ChatLogService.class);

    private final ChatLogRepository chatLogRepository;
    private final ObjectMapper objectMapper;

    public void recordChat(String sessionId,
                           String model,
                           String question,
                           String prompt,
                           String answer,
                           List<ScoredDocument> documents) {
        ChatLog chatLog = new ChatLog();
        chatLog.setSessionId(sessionId);
        chatLog.setModel(model);
        chatLog.setQuestion(question);
        chatLog.setPrompt(prompt);
        chatLog.setAnswer(answer);
        chatLog.setDocumentsJson(serializeDocuments(documents));

        chatLogRepository.save(chatLog);
    }

    private String serializeDocuments(List<ScoredDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(documents);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize scored documents for chat log", e);
            return "[]";
        }
    }
}
