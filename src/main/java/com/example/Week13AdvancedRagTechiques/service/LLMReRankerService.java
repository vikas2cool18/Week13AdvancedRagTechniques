package com.example.Week13AdvancedRagTechiques.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM-based re-ranker that uses ChatClient to intelligently rank documents
 * The LLM acts as a judge to determine relevance and importance
 */
@Service
public class LLMReRankerService {

    private final ChatClient chatClient;

    private static final String RERANKING_PROMPT = """
            You are an expert document ranking system. Given a user query and a list of documents,
            your task is to rank these documents by relevance to the query.

            Consider:
            1. Semantic relevance to the query
            2. Recency (prefer newer documents when relevant)
            3. Priority/importance indicated in metadata
            4. Completeness and quality of information

            User Query: {query}

            Documents to rank:
            {documents}

            Respond with ONLY a comma-separated list of document indices in order of relevance (most relevant first).
            For example: 2,0,3,1
            Do not include any explanation, just the indices.
            """;

    public LLMReRankerService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Re-rank documents using LLM as a judge
     * 
     * @param query     The original search query
     * @param documents List of documents to re-rank
     * @return Re-ranked list of documents
     */
    public List<Document> rerank(String query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }

        // If only one document, no need to re-rank
        if (documents.size() == 1) {
            return documents;
        }

        // Deduplicate first
        List<Document> uniqueDocs = deduplicateByContent(documents);

        // Build document list for LLM
        String documentList = buildDocumentList(uniqueDocs);

        // Get ranking from LLM
        String response = chatClient.prompt()
                .user(userSpec -> userSpec
                        .text(RERANKING_PROMPT)
                        .param("query", query != null ? query : "")
                        .param("documents", documentList))
                .call()
                .content();

        // Parse the ranking response
        List<Integer> rankedIndices = parseRankingResponse(response, uniqueDocs.size());

        // Reorder documents based on LLM ranking
        return reorderDocuments(uniqueDocs, rankedIndices);
    }

    /**
     * Build a formatted list of documents for the LLM
     */
    private String buildDocumentList(List<Document> documents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            sb.append(String.format("[%d] ", i));

            // Add metadata context
            Object year = doc.getMetadata().get("year");
            Object priority = doc.getMetadata().get("priority");
            Object dept = doc.getMetadata().get("dept");

            if (year != null || priority != null || dept != null) {
                sb.append("(");
                if (year != null)
                    sb.append("Year: ").append(year).append(", ");
                if (priority != null)
                    sb.append("Priority: ").append(priority).append(", ");
                if (dept != null)
                    sb.append("Dept: ").append(dept);
                sb.append(") ");
            }

            // Add content (truncate if too long)
            String content = doc.getText();
            if (content != null) {
                String truncated = content.length() > 200
                        ? content.substring(0, 200) + "..."
                        : content;
                sb.append(truncated);
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    /**
     * Parse the LLM's ranking response
     */
    private List<Integer> parseRankingResponse(String response, int docCount) {
        if (response == null || response.trim().isEmpty()) {
            // Fallback: return original order
            return java.util.stream.IntStream.range(0, docCount)
                    .boxed()
                    .collect(Collectors.toList());
        }

        try {
            // Parse comma-separated indices
            List<Integer> indices = Arrays.stream(response.trim().split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .filter(i -> i >= 0 && i < docCount)
                    .collect(Collectors.toList());

            // Add any missing indices at the end
            Set<Integer> usedIndices = new HashSet<>(indices);
            for (int i = 0; i < docCount; i++) {
                if (!usedIndices.contains(i)) {
                    indices.add(i);
                }
            }

            return indices;
        } catch (Exception e) {
            // Fallback: return original order
            return java.util.stream.IntStream.range(0, docCount)
                    .boxed()
                    .collect(Collectors.toList());
        }
    }

    /**
     * Reorder documents based on ranked indices
     */
    private List<Document> reorderDocuments(List<Document> documents, List<Integer> rankedIndices) {
        List<Document> reordered = new ArrayList<>();
        for (Integer index : rankedIndices) {
            if (index >= 0 && index < documents.size()) {
                reordered.add(documents.get(index));
            }
        }
        return reordered;
    }

    /**
     * Deduplicate documents by content
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
}
