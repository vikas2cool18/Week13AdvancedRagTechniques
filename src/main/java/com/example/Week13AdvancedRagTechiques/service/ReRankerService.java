package com.example.Week13AdvancedRagTechiques.service;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Re-ranker service that prioritizes documents based on metadata
 * Implements a scoring system that considers:
 * - Recency (year metadata)
 * - Priority (priority metadata)
 * - Similarity score (distance from vector search)
 */
@Service
public class ReRankerService {

    // Weights for different ranking factors
    private static final double SIMILARITY_WEIGHT = 0.4;
    private static final double RECENCY_WEIGHT = 0.3;
    private static final double PRIORITY_WEIGHT = 0.3;

    /**
     * Re-rank documents based on similarity, recency, and priority
     * 
     * @param documents List of documents to re-rank
     * @return Re-ranked list of documents
     */
    public List<Document> rerank(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }

        // First, deduplicate documents by content, keeping the best version
        List<Document> uniqueDocs = deduplicateByContent(documents);

        // Calculate composite scores for each document
        List<ScoredDocument> scoredDocs = uniqueDocs.stream()
                .map(this::calculateCompositeScore)
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .collect(Collectors.toList());

        // Return documents in ranked order
        return scoredDocs.stream()
                .map(ScoredDocument::document)
                .collect(Collectors.toList());
    }

    /**
     * Deduplicate documents by content, keeping the one with best similarity score
     */
    private List<Document> deduplicateByContent(List<Document> documents) {
        Map<String, Document> uniqueDocs = new LinkedHashMap<>();

        for (Document doc : documents) {
            String content = doc.getText();
            if (content == null)
                continue;

            if (!uniqueDocs.containsKey(content)) {
                uniqueDocs.put(content, doc);
            } else {
                // Keep the document with better similarity score (lower distance)
                Document existing = uniqueDocs.get(content);
                float existingDistance = getDistance(existing);
                float newDistance = getDistance(doc);

                if (newDistance < existingDistance) {
                    uniqueDocs.put(content, doc);
                }
            }
        }

        return new ArrayList<>(uniqueDocs.values());
    }

    /**
     * Get distance from document metadata
     */
    private float getDistance(Document doc) {
        Object distanceObj = doc.getMetadata().get("distance");
        if (distanceObj == null) {
            return Float.MAX_VALUE;
        }
        return ((Number) distanceObj).floatValue();
    }

    /**
     * Calculate composite score for a document
     */
    private ScoredDocument calculateCompositeScore(Document doc) {
        double similarityScore = calculateSimilarityScore(doc);
        double recencyScore = calculateRecencyScore(doc);
        double priorityScore = calculatePriorityScore(doc);

        // Weighted combination of all scores
        double compositeScore = (similarityScore * SIMILARITY_WEIGHT) +
                (recencyScore * RECENCY_WEIGHT) +
                (priorityScore * PRIORITY_WEIGHT);

        return new ScoredDocument(doc, compositeScore);
    }

    /**
     * Calculate similarity score from vector distance
     * Lower distance = higher similarity
     */
    private double calculateSimilarityScore(Document doc) {
        Object distanceObj = doc.getMetadata().get("distance");
        if (distanceObj == null) {
            return 0.5; // Default middle score
        }

        float distance = ((Number) distanceObj).floatValue();
        // Convert distance to similarity score (0-1 range)
        // Assuming distance is typically between 0 and 2
        return Math.max(0, 1.0 - (distance / 2.0));
    }

    /**
     * Calculate recency score based on year metadata
     * More recent years get higher scores
     */
    private double calculateRecencyScore(Document doc) {
        Object yearObj = doc.getMetadata().get("year");
        if (yearObj == null) {
            return 0.5; // Default middle score
        }

        int year = ((Number) yearObj).intValue();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);

        // Calculate years ago
        int yearsAgo = currentYear - year;

        // Score decreases with age (exponential decay)
        // Recent documents (0-2 years) get high scores (0.8-1.0)
        // Older documents get progressively lower scores
        if (yearsAgo <= 0) {
            return 1.0; // Current or future year
        } else if (yearsAgo <= 2) {
            return 0.8 + (0.2 * (2 - yearsAgo) / 2);
        } else if (yearsAgo <= 5) {
            return 0.5 + (0.3 * (5 - yearsAgo) / 3);
        } else {
            return Math.max(0.1, 0.5 - (yearsAgo - 5) * 0.05);
        }
    }

    /**
     * Calculate priority score based on priority metadata
     * Higher priority values get higher scores
     */
    private double calculatePriorityScore(Document doc) {
        Object priorityObj = doc.getMetadata().get("priority");
        if (priorityObj == null) {
            return 0.5; // Default middle score
        }

        int priority = ((Number) priorityObj).intValue();

        // Normalize priority to 0-1 range
        // Assuming priority is typically 1-10
        return Math.min(1.0, Math.max(0.0, priority / 10.0));
    }

    /**
     * Internal record to hold document with its composite score
     */
    private record ScoredDocument(Document document, double score) {
    }
}
