package com.example.Week13AdvancedRagTechiques.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for executing parallel searches and merging results
 */
@Service
public class ParallelSearchService {

    private final VectorStore vectorStore;
    private final ReRankerService reRankerService;

    public ParallelSearchService(VectorStore vectorStore, ReRankerService reRankerService) {
        this.vectorStore = vectorStore;
        this.reRankerService = reRankerService;
    }

    /**
     * Execute multiple searches in parallel and merge results
     * 
     * @param queries List of query variations to search
     * @param filters Metadata filters to apply
     * @return Merged and deduplicated list of documents
     */
    public List<Document> parallelSearch(List<String> queries, Map<String, Object> filters) {
        // Create filter expression if filters are provided
        Filter.Expression filterExpression = buildFilterExpression(filters);

        // Execute searches in parallel
        List<CompletableFuture<List<Document>>> futures = queries.stream()
                .map(query -> CompletableFuture.supplyAsync(() -> executeSearch(query, filterExpression)))
                .collect(Collectors.toList());

        // Wait for all searches to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        // Collect and merge results
        List<Document> allResults = allOf.thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList())).join();

        // Re-rank results based on similarity, recency, and priority
        return reRankerService.rerank(allResults);
    }

    /**
     * Execute a single search query
     */
    private List<Document> executeSearch(String query, Filter.Expression filterExpression) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(query);

        if (filterExpression != null) {
            requestBuilder.filterExpression(filterExpression);
        }

        return vectorStore.similaritySearch(requestBuilder.build());
    }

    /**
     * Build filter expression from metadata filters
     */
    private Filter.Expression buildFilterExpression(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }

        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        // For single filter, just return it directly
        if (filters.size() == 1) {
            Map.Entry<String, Object> entry = filters.entrySet().iterator().next();
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Integer) {
                return builder.eq(key, (Integer) value).build();
            } else if (value instanceof String) {
                return builder.eq(key, (String) value).build();
            } else if (value instanceof Boolean) {
                return builder.eq(key, (Boolean) value).build();
            }
        }

        // For multiple filters, we need to combine them
        // Create individual filter operations and combine with AND
        List<FilterExpressionBuilder.Op> ops = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Integer) {
                ops.add(builder.eq(key, (Integer) value));
            } else if (value instanceof String) {
                ops.add(builder.eq(key, (String) value));
            } else if (value instanceof Boolean) {
                ops.add(builder.eq(key, (Boolean) value));
            }
        }

        if (ops.isEmpty()) {
            return null;
        } else if (ops.size() == 1) {
            return ops.get(0).build();
        } else {
            // Combine all ops with AND
            FilterExpressionBuilder.Op result = ops.get(0);
            for (int i = 1; i < ops.size(); i++) {
                result = builder.and(result, ops.get(i));
            }
            return result.build();
        }
    }
}
