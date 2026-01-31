package com.example.Week13AdvancedRagTechiques.dto;

import java.util.Map;

/**
 * Request DTO for ingesting a single document with metadata
 */
public record DocumentIngestionRequest(
        String content,
        Map<String, Object> metadata) {
}
