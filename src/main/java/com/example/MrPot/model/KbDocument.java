package com.example.MrPot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KbDocument {
    private Long id;
    private String docType;
    private String content;
    private JsonNode metadata;
    private String embedding;
}
