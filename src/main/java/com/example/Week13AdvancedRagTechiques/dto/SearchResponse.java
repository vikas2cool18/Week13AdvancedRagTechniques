package com.example.Week13AdvancedRagTechiques.dto;

import org.springframework.ai.document.Document;
import java.util.List;

/**
 * Response DTO for search results
 */
public record SearchResponse(
        List<Document> results,
        List<String> queryVariations,
        int totalResults) {
}
