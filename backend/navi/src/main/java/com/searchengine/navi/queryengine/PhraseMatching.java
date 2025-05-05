package com.searchengine.navi.queryengine;

import java.util.regex.Pattern;

import org.bson.Document;
import org.jsoup.Jsoup;

import com.searchengine.dbmanager.DBManager;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.util.ArrayList;
import java.util.regex.Matcher;

public class PhraseMatching {

    DBManager db;

    public PhraseMatching() {
        db = new DBManager();
    }

    public String BuildStringRegex(String phrase) {
        String[] words = phrase.toLowerCase().trim().split("\\s+");

        StringBuilder regex = new StringBuilder();
        regex.append("\\b"); // Add word boundary at the start
        for (int i = 0; i < words.length; i++) {
            regex.append(Pattern.quote(words[i]));
            if (i < words.length - 1) {
                regex.append("\\s+");
            }
        }
        regex.append("\\b"); // Add word boundary at the end

        return regex.toString();
    }
    // ==================================================================================
    // ==========================NOTE: For testing purpose
    // only==========================
    // ==================================================================================
    // build a function to return ids of document that has the phrase
    // 1. get the phrase
    // 2. get the regex
    // 3. get the list of documents
    // 4. loop over the documents and check if the regex matches

    public ArrayList<String> getMatchedDocs(String phrase) {
        ArrayList<Document> docs = db.getDocumentsContent();
        ArrayList<String> matchedDocs = new ArrayList<>();

        String normalizedPhrase = phrase.toLowerCase().trim().replaceAll("\\s+", " ");
        String regex = BuildStringRegex(normalizedPhrase);
        Pattern pattern = Pattern.compile(regex);

        docs.parallelStream().forEach(doc -> {
            String text = doc.getString("content").toLowerCase().trim().replaceAll("\\s+", " ");

            if (text.contains(normalizedPhrase)) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    synchronized (matchedDocs) {
                        matchedDocs.add(doc.getString("title"));
                    }
                }
            }
        });

        return matchedDocs;
    }

    public static void main(String[] args) {
        PhraseMatching pm = new PhraseMatching();
        ArrayList<String> matchedDocs = new ArrayList<>();
        {
            long startTime = System.currentTimeMillis();
            matchedDocs = pm.getMatchedDocs("Very Free new Pagesssssss");
            long endTime = System.currentTimeMillis();
            System.out.println("Time taken: " + (endTime - startTime) + " milliseconds");
        }
        for (String doc : matchedDocs) {
            System.out.println(doc + "===================================");
        }
    }
}
