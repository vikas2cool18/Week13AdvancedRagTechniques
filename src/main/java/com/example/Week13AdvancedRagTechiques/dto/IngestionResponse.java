package com.example.Week13AdvancedRagTechiques.dto;

/**
 * Response DTO for document ingestion
 */
public record IngestionResponse(
        String message,
        int documentsIngested) {
}
