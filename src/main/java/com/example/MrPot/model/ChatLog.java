package com.example.MrPot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "chat_logs")
@Getter
@Setter
public class ChatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId;

    private String model;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String question;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String prompt;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String answer;

    @Lob
    @Column(name = "documents", columnDefinition = "TEXT")
    private String documentsJson;

    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
