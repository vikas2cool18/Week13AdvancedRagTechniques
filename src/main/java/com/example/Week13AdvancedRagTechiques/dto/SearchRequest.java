package com.example.Week13AdvancedRagTechiques.dto;

import java.util.Map;

/**
 * Request DTO for search with query and filters
 */
public record SearchRequest(
                String query,
                Map<String, Object> filters) {
}
