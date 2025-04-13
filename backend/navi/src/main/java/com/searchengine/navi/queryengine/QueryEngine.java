package com.searchengine.navi.queryengine;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import opennlp.tools.stemmer.PorterStemmer;

public class QueryEngine {

    private HashSet<String> stopWords;

    public QueryEngine() {
        addStopWords();
    }

    public List<Object> parseQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<Object> result = new ArrayList<>();
        PorterStemmer stemmer = new PorterStemmer();
        query = query.trim().toLowerCase();

        // Split query into tokens (phrases and operators)
        List<String> tokens = tokenizeQuery(query);
        if (tokens == null) {
            System.out.println("Invalid query: Unmatched quotes or malformed input");
            return new ArrayList<>();
        }

        // Process tokens
        int operatorCount = 0;
        List<String> currentPhrase = null;
        boolean expectPhrase = true;

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            if (expectPhrase) {
                if (isOperator(token)) {
                    System.out.println("Invalid query: Unexpected operator at position " + i);
                    return new ArrayList<>();
                }
                currentPhrase = processPhrase(token, stemmer);
                if (!currentPhrase.isEmpty()) {
                    result.add(currentPhrase);
                } else if (result.isEmpty()) {
                    return new ArrayList<>();
                }
                expectPhrase = false;

            } else {

                if (!isOperator(token)) {
                    System.out.println("Invalid query: Expected operator, found " + token);
                    return new ArrayList<>();
                }

                result.add(token.toUpperCase());
                operatorCount++;
                expectPhrase = true;

                if (operatorCount > 2) {
                    System.out.println("Invalid query: Maximum of two operations allowed");
                    return new ArrayList<>();
                }
            }
        }

        // Validate the query ends with a phrase
        if (expectPhrase) {
            System.out.println("Invalid query: Query ends with an operator");
            return new ArrayList<>();
        }

        return result;
    }

    private List<String> tokenizeQuery(String query) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        boolean inQuotes = false;
        StringBuilder token = new StringBuilder();

        while (i < query.length()) {
            char c = query.charAt(i);

            if (c == '"') {
                if (inQuotes) {
                    // End of quoted phrase
                    tokens.add(token.toString());
                    token.setLength(0);
                    inQuotes = false;
                } else {
                    // Start of quoted phrase
                    inQuotes = true;
                }
                i++;
            } else if (inQuotes) {
                token.append(c);
                i++;
            } else if (Character.isWhitespace(c)) {
                // Handle whitespace outside quotes
                if (token.length() > 0) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
                i++;
            } else {
                token.append(c);
                i++;
            }
        }

        // Add the last token if present
        if (token.length() > 0) {
            if (inQuotes) {
                // Unclosed quote
                return null;
            }
            tokens.add(token.toString());
        }

        return tokens;
    }

    private List<String> processPhrase(String phrase, PorterStemmer stemmer) {
        List<String> processed = new ArrayList<>();
        // Clean and split phrase
        String cleaned = phrase.replaceAll("[^a-z0-9\\s]", "");
        String[] words = cleaned.split("\\s+");

        // Stem and filter stop words
        for (String word : words) {
            if (!word.isEmpty()) {
                String stemmed = stemmer.stem(word);
                if (!stopWords.contains(stemmed) && !stemmed.isEmpty()) {
                    processed.add(stemmed);
                }
            }
        }

        return processed;
    }

    private boolean isOperator(String token) {
        return token.equalsIgnoreCase("and") ||
                token.equalsIgnoreCase("or") ||
                token.equalsIgnoreCase("not");
    }

    private void addStopWords() {
        stopWords = new HashSet<>();
        try (BufferedReader scanner = new BufferedReader(new FileReader("./stopwords.txt"))) {
            String line;
            while ((line = scanner.readLine()) != null) {
                stopWords.add(line.trim().toLowerCase());
            }
        } catch (IOException e) {
            System.out.println("Error loading stop words: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        QueryEngine engine = new QueryEngine();
        List<Object> result;

        // Valid queries
        result = engine.parseQuery("\"Football player\" OR \"Tennis player\"");
        System.out.println(result);
        // Output: [[footbal, player], OR, [tenni, player]]

        result = engine.parseQuery("\"Football player\" AND \"Tennis player\" NOT \"Soccer star\"");
        System.out.println(result);
        // Output: [[footbal, player], AND, [tenni, player], NOT, [soccer, star]]

        result = engine.parseQuery("\"Football player\"");
        System.out.println(result);
        // Output: [[footbal, player]]

        // Invalid queries
        result = engine
                .parseQuery("\"Football player\" AND \"Tennis player\" OR \"Soccer star\" NOT \"Basketball star\"");
        // Output: Invalid query: Maximum of two operations allowed
        // Returns: []

        result = engine.parseQuery("\"Football player\" AND");
        // Output: Invalid query: Query ends with an operator
        // Returns: []

        result = engine.parseQuery("\"Football player\" \"Tennis player\"");
        // Output: Invalid query: Expected operator, found Tennis player
        // Returns: []

        result = engine.parseQuery("\"Football player");
        // Output: Invalid query: Unmatched quotes or malformed input
        // Returns: []
    }
}