package com.example.Week13AdvancedRagTechiques.dto;

import java.util.List;

/**
 * Request DTO for batch document ingestion
 */
public record DocumentIngestionBatchRequest(
                List<DocumentIngestionRequest> documents) {
}
