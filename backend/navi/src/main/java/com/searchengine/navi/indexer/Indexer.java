package com.searchengine.navi.indexer;

// import org.bson.Document;
import org.jsoup.nodes.Document;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.Buffer;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Indexer {
    private HashSet<String> stopWords;

    public Indexer() {
        stopWords = new HashSet<String>();
    }

    public class Token {
        String word, position;
        int count;

        Token(String word, String position) {
            if (position == null || position.isEmpty()) {
                position = "text";
            }
            this.word = word;
            this.count = 1;
            this.position = position;
        }

        void increment() {
            count++;
        }

    }
    // TO_BE_Continue

    // public List<String> tokenizeDocument(Document doc) {
    // String text = doc.text();
    // List<String> tokens = tokenizeText(text);
    // HashMap<String, Token> hashTokens = new HashMap<String, Token>();

    // for (String key : hashTokens.keySet()) {
    // Token token = hashTokens.get(key);
    // if (hashTokens.containsKey(key)) {
    // token.increment();
    // } else {
    // hashTokens.put(key, new Token(key, ""));
    // }
    // }

    // return tokens;
    // }

    public List<String> tokenizeText(String text) {
        List<String> tokens = new ArrayList<String>();
        String restructureText = text.toLowerCase().replaceAll("[^a-z]", "");
        String[] arrList = restructureText.split("\\s+");
        for (String s : arrList) {
            tokens.add(s.trim());
        }
        return tokens;
    }

    public void addStopWords() {
        try {
            BufferedReader scanner = new BufferedReader(new FileReader("stopwords.txt"));
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

    public static void main(String[] args) {
        // System.out.println("lol");
    }
}
