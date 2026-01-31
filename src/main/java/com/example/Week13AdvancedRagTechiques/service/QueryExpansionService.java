package com.example.Week13AdvancedRagTechiques.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for expanding search queries into multiple variations
 * to improve retrieval breadth
 */
@Service
public class QueryExpansionService {

    private final ChatClient chatClient;

    private static final String EXPANDER_PROMPT = """
            You are a search expert. Given the user question, generate 3 concise alternative versions to improve vector search retrieval.
            Original: {question}
            Output format: One variation per line.
            """;

    public QueryExpansionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Generate query variations for improved search retrieval
     * 
     * @param originalQuery The original user query
     * @return List containing the original query plus 3 variations
     */
    public List<String> expandQuery(String originalQuery) {
        // Generate variations using ChatClient
        String response = chatClient.prompt()
                .user(userSpec -> userSpec
                        .text(EXPANDER_PROMPT)
                        .param("question", originalQuery != null ? originalQuery : ""))
                .call()
                .content();

        // Parse the response into individual variations
        List<String> variations = response != null
                ? Arrays.stream(response.split("\n"))
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .collect(Collectors.toList())
                : new ArrayList<>();

        // Include the original query as well
        variations.add(0, originalQuery);

        return variations;
    }
}
