package com.example.Week13AdvancedRagTechiques.controller;

import com.example.Week13AdvancedRagTechiques.dto.*;
import com.example.Week13AdvancedRagTechiques.service.LLMReRankerService;
import com.example.Week13AdvancedRagTechiques.service.ParallelSearchService;
import com.example.Week13AdvancedRagTechiques.service.QueryExpansionService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for RAG operations including document ingestion and search
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

        private final VectorStore vectorStore;
        private final QueryExpansionService queryExpansionService;
        private final ParallelSearchService parallelSearchService;
        private final LLMReRankerService llmReRankerService;

        public RagController(
                        VectorStore vectorStore,
                        QueryExpansionService queryExpansionService,
                        ParallelSearchService parallelSearchService,
                        LLMReRankerService llmReRankerService) {
                this.vectorStore = vectorStore;
                this.queryExpansionService = queryExpansionService;
                this.parallelSearchService = parallelSearchService;
                this.llmReRankerService = llmReRankerService;
        }

        /**
         * Ingest documents with metadata into the vector store
         * 
         * @param request Batch request containing documents with content and metadata
         * @return Response with ingestion status
         */
        @PostMapping(value = "/ingest", consumes = "application/json", produces = "application/json")
        public ResponseEntity<IngestionResponse> ingestDocuments(
                        @RequestBody DocumentIngestionBatchRequest request) {

                // Convert DTOs to Spring AI Documents
                List<Document> documents = request.documents().stream()
                                .map(doc -> new Document(
                                                doc.content() != null ? doc.content() : "",
                                                doc.metadata() != null ? doc.metadata() : new HashMap<>()))
                                .collect(Collectors.toList());

                // Store documents in vector store
                if (!documents.isEmpty()) {
                        vectorStore.add(documents);
                }

                return ResponseEntity.ok(new IngestionResponse(
                                "Documents successfully ingested",
                                documents.size()));
        }

        /**
         * Search documents with query rewriting and metadata filters
         * 
         * @param request Search request with query and filters
         * @return Search response with results and query variations
         */
        @PostMapping(value = "/search", consumes = "application/json", produces = "application/json")
        public ResponseEntity<SearchResponse> search(@RequestBody SearchRequest request) {

                // Step 1: Generate query variations using ChatClient
                List<String> queryVariations = queryExpansionService.expandQuery(request.query());

                // Step 2: Execute parallel searches with all variations
                List<Document> results = parallelSearchService.parallelSearch(
                                queryVariations,
                                request.filters());

                // Step 3: Return merged results with query variations
                return ResponseEntity.ok(new SearchResponse(
                                results,
                                queryVariations,
                                results.size()));
        }

        /**
         * Search documents with LLM-based re-ranking
         * Uses an LLM to intelligently judge and rank results
         * 
         * @param request Search request with query and filters
         * @return Search response with LLM re-ranked results and query variations
         */
        @PostMapping(value = "/search-llm", consumes = "application/json", produces = "application/json")
        public ResponseEntity<SearchResponse> searchWithLLMReranking(@RequestBody SearchRequest request) {

                // Step 1: Generate query variations using ChatClient
                List<String> queryVariations = queryExpansionService.expandQuery(request.query());

                // Step 2: Execute parallel searches with all variations
                List<Document> initialResults = parallelSearchService.parallelSearch(
                                queryVariations,
                                request.filters());

                // Step 3: Use LLM to re-rank results intelligently
                List<Document> rerankedResults = llmReRankerService.rerank(
                                request.query(),
                                initialResults);

                // Step 4: Return LLM re-ranked results with query variations
                return ResponseEntity.ok(new SearchResponse(
                                rerankedResults,
                                queryVariations,
                                rerankedResults.size()));
        }
}
