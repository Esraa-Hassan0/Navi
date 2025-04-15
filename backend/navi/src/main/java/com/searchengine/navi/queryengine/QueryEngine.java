package com.searchengine.navi.queryengine;
// package com.searchengine.backend.queryengine;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.springframework.web.bind.annotation.*;
import opennlp.tools.stemmer.PorterStemmer;
import com.searchengine.dbmanager.DBManager;
import java.util.regex.Matcher;

@RestController
@RequestMapping("/")
@CrossOrigin(origins = "*") // Allow React app to connect
public class QueryEngine {

    private HashSet<String> stopWords;
    private DBManager dbManager;
    private List<String> tokens = new ArrayList<>(); // Store parsed tokens for snippet generation
    int resultCount = 0; // Counter for the number of results found
    int suggestionCount = 0; // Counter for the number of suggestions found

    public static class Phrase {
        private List<String> words;
        private boolean isQuoted;

        public Phrase(List<String> words, boolean isQuoted) {
            this.words = words;
            this.isQuoted = isQuoted;
        }

        public List<String> getWords() {
            return words;
        }

        public boolean isQuoted() {
            return isQuoted;
        }
    }

    public QueryEngine() {
        addStopWords();
        dbManager = new DBManager();

    }

    @GetMapping("/")
    public String index() {
        return "Query Engine is running!";
    }

    public List<Object> parseQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<Object> result = new ArrayList<>();
        PorterStemmer stemmer = new PorterStemmer();
        query = query.trim().toLowerCase();

        // Split query into tokens (phrases and operators)
        tokens = tokenizeQuery(query);
        if (tokens == null) {
            System.out.println("Invalid query: Unmatched quotes or malformed input");
            return new ArrayList<>();
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
                boolean isQuoted = token.startsWith("\"") && token.endsWith("\"");
                if (isQuoted) {
                    // Remove quotes for processing
                    token = token.substring(1, token.length() - 1);
                }
                List<String> words = processPhrase(token, stemmer);
                if (!words.isEmpty()) {
                    result.add(new Phrase(words, isQuoted));
                } else if (result.isEmpty()) {
                    // If the first phrase is empty (all stop words), return empty
                    return new ArrayList<>();
                }
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
        String content = Jsoup.parse(docURL).text(); // Assuming docID is a URL or HTML content

        // Collect query terms for matching
        List<String> allWords = new ArrayList<>();
        List<List<String>> quotedPhrases = new ArrayList<>();
        List<String> notWords = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            Object element = tokens.get(i);
            if (element instanceof Phrase) {
                Phrase phrase = (Phrase) element;
                List<String> words = phrase.getWords();
                if (i > 0 && tokens.get(i - 1).equals("NOT")) {
                    notWords.addAll(words);
                } else {
                    if (phrase.isQuoted()) {
                        quotedPhrases.add(words);
                    } else {
                        allWords.addAll(words);
                    }
                }
            }
        }

        // Remove duplicates
        List<String> uniqueWords = new ArrayList<>(new HashSet<>(allWords));
        return generateSnippet(content, uniqueWords, quotedPhrases, notWords);
    }

    private String generateSnippet(String content, List<String> uniqueWords, List<List<String>> quotedPhrases,
            List<String> notWords) {
        StringBuilder snippet = new StringBuilder("... ");
        String contentLower = content.toLowerCase();

        // Highlight quoted phrases first
        for (List<String> phrase : quotedPhrases) {
            StringBuilder phraseText = new StringBuilder();
            for (int i = 0; i < phrase.size(); i++) {
                phraseText.append(phrase.get(i));
                if (i < phrase.size() - 1) {
                    phraseText.append("\\s+");
                }
            }
            Pattern pattern = Pattern.compile(phraseText.toString(), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(contentLower);
            if (matcher.find()) {
                int start = Math.max(0, matcher.start() - 20);
                int end = Math.min(content.length(), matcher.end() + 20);
                String excerpt = content.substring(start, end);
                // Highlight the phrase
                String phraseStr = content.substring(matcher.start(), matcher.end());
                excerpt = excerpt.replaceAll("(?i)" + phraseText, "**" + phraseStr + "**");
                snippet.append(excerpt).append(" ... ");
                return snippet.toString();
            }
        }

        // Highlight individual words
        for (String word : uniqueWords) {
            Pattern pattern = Pattern.compile("\\b" + word + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(contentLower);
            if (matcher.find()) {
                int start = Math.max(0, matcher.start() - 20);
                int end = Math.min(content.length(), matcher.end() + 20);
                String excerpt = content.substring(start, end);
                // Highlight the word
                excerpt = excerpt.replaceAll("(?i)\\b" + word + "\\b", "**" + word + "**");
                snippet.append(excerpt).append(" ... ");
                return snippet.toString();
            }
        }

        // If no matches, return a default portion of the content
        int end = Math.min(50, content.length());
        snippet.append(content.substring(0, end)).append(" ... ");
        return snippet.toString();
    }

    private void getSuggesstions() {

    }

    private void getResults() {

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