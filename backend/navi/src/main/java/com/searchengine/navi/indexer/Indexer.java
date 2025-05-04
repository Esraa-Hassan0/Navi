package com.searchengine.navi.indexer;

import org.bson.Document;
import org.bson.types.ObjectId;

//import org.jsoup.nodes.Document;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.Buffer;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.Gson;
import com.searchengine.dbmanager.DBManager;

import ch.qos.logback.core.joran.sanity.Pair;
import opennlp.tools.stemmer.PorterStemmer;

public class Indexer {
    static final String RESET = "\u001B[0m";
    static final String TEAL = "\u001B[36m";
    static final String YELLOW = "\u001B[33m";
    static final String RED = "\u001B[31m";
    static final String GREEN = "\u001B[32m";
    static final String PURPLE = "\u001B[35m";

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z\\s]");

    PorterStemmer stemmer = new PorterStemmer();
    static DBManager dbmanager;
    private HashSet<String> stopWords;
    HashMap<String, Token> tokenMap = new HashMap<>();

    public Indexer() {
        dbmanager = new DBManager();
        stopWords = new HashSet<String>();
        addStopWords();
    }

    public class Token {
        private String word;
        private ArrayList<Posting> postings;

        Token(String word) {
            this.word = word;
            this.postings = new ArrayList<>();
        }

        public void addPostings(Posting posting) {
            this.postings.add(posting);
        }

        public ArrayList<Posting> getPostings() {
            return postings;
        }
    }

    public void calcFields(Document doc) {
        String url = doc.getString("url");
        ObjectId id = doc.getObjectId("_id");

        String a = dbmanager.getDocAnchorById(url);
        String H1 = dbmanager.getDocH1ById(url);
        String H2 = dbmanager.getDocH2ById(url);
        String content = dbmanager.getDocContentByURL(url);


        // Remove stopwords
        a = removeStopwords(a, stopWords);
        H1 = removeStopwords(H1, stopWords);
        H2 = removeStopwords(H2, stopWords);
        content = removeStopwords(content, stopWords);

        int ACount = countWords(a);
        int H1Count = countWords(H1);
        int H2Count = countWords(H2);
        int otherCount = countWords(content)-ACount-H1Count-H2Count;

        dbmanager.appendTagCounts(id, H1Count, H2Count, ACount,otherCount);

    }

    public String removeStopwords(String content, Set<String> stopWords) {
        StringBuilder result = new StringBuilder();
        String[] words = content.split("\\s+");

        for (String word : words) {
            String cleaned = word.replaceAll("[^a-zA-Z]", "").toLowerCase();
            if (!stopWords.contains(cleaned)) {
                result.append(word).append(" ");
            }
        }

        return result.toString().trim();
    }

    public static int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        String[] words = text.trim().split("\\s+");
        return words.length;
    }

    public HashMap<String, Token> tokenizeDocument(Document doc, HashMap<String, Token> tokenMap) {
        // String url = doc.location();
        // int docId = dbmanager.retrieveDocID(url);
        // String text = doc.text();

        String url = doc.getString("url");
        ObjectId docId = dbmanager.retrieveDocID(url);
        String text = doc.getString("content");
        String title = doc.getString("title");

        // System.out.println(PURPLE);
        // System.out.println(text);
        // System.out.println(RESET);

        // Elements h1Tags = doc.select("h1");
        // Elements h2Tags = doc.select("h2");
        // Elements anchorTags = doc.select("a[href]");
        // Elements h1Tags = doc.select("h1");
        // Elements h2Tags = doc.select("h2");
        // Elements anchorTags = doc.select("a[href]");

        String h1Tags = doc.getString("h1");
        String h2Tags = doc.getString("h2");
        String anchorTags = doc.getString("a");

        // System.out.println(PURPLE + "H1 Tags: " + h1Tags.text() + RESET);
        // tokenizeText(h1Tags.text(), tokenMap, docId, "h1");

        // System.out.println(PURPLE + "H2 Tags: " + h2Tags.text() + RESET);
        // tokenizeText(h2Tags.text(), tokenMap, docId, "h2");

        // System.out.println(PURPLE + "Anchor Tags: " + anchorTags.text() + RESET);
        // tokenizeText(anchorTags.text(), tokenMap, docId, "a");

        System.out.println(PURPLE + "H1 Tags: " + h1Tags + RESET);
        tokenizeText(h1Tags, tokenMap, docId, "h1");
        tokenizeText(title, tokenMap, docId, "h1");

        System.out.println(PURPLE + "H2 Tags: " + h2Tags + RESET);
        tokenizeText(h2Tags, tokenMap, docId, "h2");

        // System.out.println(PURPLE + "Anchor Tags: " + anchorTags + RESET);
        tokenizeText(anchorTags, tokenMap, docId, "a");

        tokenizeText(text, tokenMap, docId, "other");
        return tokenMap;
    }

    public void tokenizeText(String text, HashMap<String, Token> tokenMap, ObjectId docId, String type) {
        // String restructureText = text.toLowerCase().replaceAll("[^a-zA-Z\\s]", "");
        String restructureText = NON_ALPHANUMERIC.matcher(text.toLowerCase()).replaceAll("");
        String[] arrList = restructureText.split("\\s+");
        System.out.println(TEAL + "Tokens before filtering (" + type + "): " + Arrays.toString(arrList) + RESET);
        int counter = 0;
        // Stemmer languageStemmer = new Stemmer();
        for (String s : arrList) {
            // to be stemmed
            String tokenStr = s.trim();
            System.out.println(GREEN + "===========BEFORE STEMMING===========\n" + PURPLE + tokenStr + RESET);
            // tokenStr = languageStemmer.stemWord(tokenStr);
            try {

                System.out.println("Stemmed '" + "' to '" + tokenStr + "'");
            } catch (Exception e) {
                System.err.println("Error stemming '" + tokenStr + "': " + e.getMessage());
            }
            System.out.println(GREEN + "===========AFTER STEMMING============\n" + YELLOW + tokenStr + RESET);

            if (!tokenStr.isEmpty() && !stopWords.contains(tokenStr)) {
                tokenStr = stemmer.stem(tokenStr);
                counter++;
                Token token = tokenMap.get(tokenStr);
                if (token == null) {
                    token = new Token(tokenStr);
                    tokenMap.put(tokenStr, token);
                }
                Posting posting = null;
                for (Posting p : token.getPostings()) {
                    if (p.getDocID() == docId) {
                        posting = p;
                        break;
                    }
                }
                if (posting == null) {
                    posting = new Posting(docId);
                    token.addPostings(posting);
                }
                posting.addPosition(type);
            }
        }
    }

    public void addStopWords() {
        try {
            BufferedReader scanner = new BufferedReader(new FileReader("backend/navi/Data/stopWords.txt"));
            String line;
            while ((line = scanner.readLine()) != null) {
                stopWords.add(line.trim());
            }
            scanner.close();
        } catch (IOException e) {
            System.out.println(e);
            return;
        }
    }

    public void close() {
        dbmanager.close();
    }

    public static void main(String[] args) {
        System.out.println(GREEN + "Starting tokenization..." + RESET);
        try {
            // Document doc =
            // Jsoup.connect("https://toolsfairy.com/code-test/sample-html-files#").get();
            Document doc = dbmanager.getUnindexedDocuments().first();

            Indexer indexer = new Indexer();
            HashMap<String, Token> tokenMap = new HashMap<>();
            tokenMap = indexer.tokenizeDocument(doc, tokenMap);

            System.out.println(GREEN + "Tokenized Words:" + RESET);
            if (tokenMap.isEmpty()) {
                System.out.println("No tokens found.");
            } else {
                for (String word : tokenMap.keySet()) {
                    Token token = tokenMap.get(word);
                    Posting posting = token.getPostings().get(0);
                    System.out.print(GREEN + "Word: " + word + RESET + " ->   DocId: " +
                            posting.getDocID() + ", TF: " + posting.getTF() + ", Types: {");
                    Map<String, Integer> typeMap = posting.getTypeCounts();
                    int i = 0;
                    for (Map.Entry<String, Integer> entry : typeMap.entrySet()) {
                        System.out.print(entry.getKey() + ": " + entry.getValue());
                        if (i < typeMap.size() - 1) {
                            System.out.print(", ");
                        }
                        i++;
                    }
                    System.out.println("}");
                }
            }
            indexer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(GREEN + "Done." + RESET);
    }
}