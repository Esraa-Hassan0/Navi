package com.searchengine.navi.queryengine;
// package com.searchengine.backend.queryengine;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.jsoup.Jsoup;
import org.springframework.web.bind.annotation.*;
import opennlp.tools.stemmer.PorterStemmer;
import com.searchengine.dbmanager.DBManager;
import com.searchengine.navi.Ranker.Ranker;

import java.util.regex.Matcher;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/")
public class QueryEngine {

    private HashSet<String> stopWords;
    private DBManager dbManager;
    private Ranker r;
    private ArrayList<String> tokens = new ArrayList<>(); // Store parsed tokens for snippet generation
    int resultCount = 0; // Counter for the number of results found
    int suggestionCount = 0; // Counter for the number of suggestions found

    public static class Phrase {
        private String phrase = "";
        private boolean isQuoted = false;

        public Phrase(String phrase, boolean isQuoted) {
            this.phrase = phrase;
            this.isQuoted = isQuoted;
        }

        public String getPhrase() {
            return phrase;
        }

        public boolean isQuoted() {
            return isQuoted;
        }
    }

    private Phrase phraseQuery;

    public QueryEngine() {
        addStopWords();
        dbManager = new DBManager();

    }

    @GetMapping("/home")
    public String index() {
        return "Query Engine is running!";
    }

    @PostMapping("/search")
    public ArrayList<String> parseQuery(@RequestParam("query") String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        dbManager.insertSuggestion(query);

        ArrayList<String> result = new ArrayList<>();
        PorterStemmer stemmer = new PorterStemmer();
        query = query.trim().toLowerCase();

        // Split query into tokens (phrases and operators)
        tokens = tokenizeQuery(query);
        if (tokens == null) {
            System.out.println("Invalid query: Unmatched quotes or malformed input");
            return new ArrayList<>();
        }
        boolean isQuoted = query.startsWith("\"") && query.endsWith("\"");
        if (!isQuoted) {
            // Not phrases, just words
            return new ArrayList<>(tokens.stream()
                    .filter(token -> !stopWords.contains(token) && !token.isEmpty())
                    .map(stemmer::stem)
                    .collect(Collectors.toList()));
        }
        // Process tokens
        int operatorCount = 0;
        boolean expectPhrase = true; // Start by expecting a phrase

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            if (expectPhrase) {
                if (isOperator(token)) {
                    System.out.println("Invalid query: Unexpected operator at position " + i);
                    return new ArrayList<>();
                }

                // Process phrase (quoted or unquoted)
                isQuoted = token.startsWith("\"") && token.endsWith("\"");
                if (isQuoted) {
                    // Remove quotes for processing
                    token = token.substring(1, token.length() - 1);

                    // TODO: CALL function TO GET THE PHRASE and then matched docs
                    phraseQuery = new Phrase(token, true);

                }
                String phrase = processPhrase(token, stemmer);

                result.add(phrase);

                expectPhrase = false;

            } else {
                // Expect an operator
                if (!isOperator(token)) {
                    System.out.println("Invalid query: Expected operator, found " + token);
                    return new ArrayList<>();
                }

                result.add(token.toUpperCase());
                operatorCount++;
                expectPhrase = true;

                // Check operator limit
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

        System.out.println("Parsed query: " + result.stream().map(Object::toString).collect(Collectors.joining(" "))); // Debug

        return result;
    }

    private ArrayList<String> tokenizeQuery(String query) {
        ArrayList<String> tokens = new ArrayList<>();
        int i = 0;
        boolean inQuotes = false;
        StringBuilder token = new StringBuilder();

        while (i < query.length()) {
            char c = query.charAt(i);

            if (c == '"') {
                if (inQuotes) {
                    // End of quoted phrase
                    token.append(c);
                    tokens.add(token.toString());
                    token.setLength(0);
                    inQuotes = false;
                } else {
                    // Start of quoted phrase
                    if (token.length() > 0) {
                        tokens.add(token.toString());
                        token.setLength(0);
                    }
                    token.append(c);
                    inQuotes = true;
                }
                i++;
            } else if (inQuotes) {
                // Collect characters inside quotes
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
                // Collect characters for unquoted tokens
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

    private String processPhrase(String phrase, PorterStemmer stemmer) {
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
        StringBuilder sb = new StringBuilder();
        for (String word : processed) {
            sb.append(word).append(" ");
        }
        // Remove trailing space
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1); // Remove last space
        }
        // Return the processed phrase as a single string
        return sb.toString();
    }

    private boolean isOperator(String token) {
        return token.equalsIgnoreCase("and") ||
                token.equalsIgnoreCase("or") ||
                token.equalsIgnoreCase("not");
    }

    private void addStopWords() {
        stopWords = new HashSet<>();
        try (BufferedReader scanner = new BufferedReader(new FileReader("./Data/stopwords.txt"))) {
            String line;
            while ((line = scanner.readLine()) != null) {
                stopWords.add(line.trim().toLowerCase());
            }
        } catch (IOException e) {
            System.out.println("Error loading stop words: " + e.getMessage());
        }
    }

    public String getSnippet(String docURL) {
        String content;
        try {
            content = Jsoup.connect(docURL)
                    .userAgent("Mozilla/5.0")
                    .get()
                    .text();
        } catch (IOException e) {
            return "Unable to fetch content.";
        }

        String contentLower = content.toLowerCase();
        List<String> lowerTokens = tokens.stream()
                .map(t -> t.toLowerCase().replaceAll("\"", ""))
                .collect(Collectors.toList());

        int bestStart = 0;
        int maxCount = 0;

        for (int i = 0; i < content.length() - 400; i += 50) {
            int end = Math.min(content.length(), i + 400);
            String window = contentLower.substring(i, end);
            int count = 0;
            for (String token : lowerTokens) {
                if (window.contains(token)) {
                    count++;
                }
            }
            if (count > maxCount) {
                maxCount = count;
                bestStart = i;
            }
        }

        int snippetEnd = Math.min(content.length(), bestStart + 400);
        String snippetRaw = content.substring(bestStart, snippetEnd);

        // Highlight tokens in the original-case snippet
        for (String token : lowerTokens) {
            snippetRaw = snippetRaw.replaceAll("(?i)\\b" + token + "\\b", "<b>" + token + "</b>");

        }

        return "... " + snippetRaw.trim() + " ...";
    }

    // private String generateSnippet(String content) {
    // StringBuilder snippet = new StringBuilder("... ");
    // String contentLower = content.toLowerCase();

    // // // Highlight quoted phrases first
    // // for (List<String> phrase : quotedPhrases) {
    // // StringBuilder phraseText = new StringBuilder();
    // // for (int i = 0; i < phrase.size(); i++) {
    // // phraseText.append(phrase.get(i));
    // // if (i < phrase.size() - 1) {
    // // phraseText.append("\\s+");
    // // }
    // // }
    // // Pattern pattern = Pattern.compile(phraseText.toString(),
    // // Pattern.CASE_INSENSITIVE);
    // // Matcher matcher = pattern.matcher(contentLower);
    // // if (matcher.find()) {
    // // int start = Math.max(0, matcher.start() - 20);
    // // int end = Math.min(content.length(), matcher.end() + 20);
    // // String excerpt = content.substring(start, end);
    // // // Highlight the phrase
    // // String phraseStr = content.substring(matcher.start(), matcher.end());
    // // excerpt = excerpt.replaceAll("(?i)" + phraseText, "**" + phraseStr +
    // "**");
    // // snippet.append(excerpt).append(" ... ");
    // // return snippet.toString();
    // // }
    // // }

    // Set<String> seen = new HashSet<>();
    // for (String word : tokens) {
    // if (seen.contains(word.toLowerCase()))
    // continue;
    // seen.add(word.toLowerCase());

    // Pattern pattern = Pattern.compile("\\b" + Pattern.quote(word) + "\\b",
    // Pattern.CASE_INSENSITIVE);
    // Matcher matcher = pattern.matcher(contentLower);

    // if (matcher.find()) {
    // int start = Math.max(0, matcher.start() - 30);
    // int end = Math.min(content.length(), matcher.end() + 30);
    // String excerpt = content.substring(start, end);
    // // Highlight the word (preserve original casing)
    // excerpt = excerpt.replaceAll("(?i)\\b" + Pattern.quote(word) + "\\b", "**" +
    // word + "**");
    // snippet.append(excerpt).append(" ... ");
    // }
    // }

    // // return snippet.toString();

    // // If no matches, return a default portion of the content
    // int end = Math.min(50, content.length());
    // snippet.append(content.substring(0, end)).append(" ... ");
    // System.out.println("Snippet: " + snippet.toString()); // Debug
    // return snippet.toString();
    // }

    @GetMapping("/suggestions")
    public List<String> getSuggestions(@RequestParam("query") String query) {
        List<String> suggestions = new ArrayList<>();
        suggestions = dbManager.getSuggestions(query);
        return suggestions;
    }

    @GetMapping("/results")
    public Document getResults() {
        // Initialize Ranker with search tokens
        // ArrayList<String> tokens = new ArrayList<>();
        // tokens.add("been");
        r = new Ranker(tokens, phraseQuery.getPhrase(), phraseQuery.isQuoted());
        r.sortDocs();

        // Get ranked document IDs
        List<Integer> docIds = r.sortDocs();

        // Fetch documents using DB manager
        System.out.println("docIds: " + docIds); // Debug
        List<Document> results = dbManager.getDocumentsByID(docIds);
        System.out.println("Results: " + results); // Debug
        int availableCount = resultCount; // assume this is set somewhere earlier

        // Process documents
        for (Document result : results) {
            String docURL = result.getString("url");
            String snippet = getSnippet(docURL); // use your actual snippet logic
            result.remove("content");
            result.remove("_id");
            result.append("snippets", snippet);
            if (snippet == null) {
                availableCount--;
            }
        }

        // Assemble response
        Document data = new Document("results", results);
        // .append("count", resultCount)
        // .append("availableCount", availableCount);

        return data;
    }

    // For testing
    public static void main(String[] args) {
        QueryEngine engine = new QueryEngine();
        String[] queries = {
                "\"Football player scores\"",
                "Football player scores",
                "\"Football player\" OR \"Tennis player\"",
                "\"Football player\" AND \"Cricket star\" NOT \"Soccer star\""
        };
        for (String query : queries) {
            System.out.println("Query: " + query);
            System.out.println("Parsed: " + engine.parseQuery(query));
            System.out.println();
        }
    }
}