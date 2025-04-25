package com.searchengine.navi.indexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import org.bson.Document;
import org.jsoup.Jsoup;
import com.searchengine.dbmanager.DBManager;
import com.searchengine.navi.indexer.Indexer.Token;

public class Main {
    public static void main(String[] args) {
        // Create a single DBManager instance
        DBManager db = new DBManager();
        Indexer indexer = new Indexer(); // Pass DB reference to indexer

        try {
            // Retrieve urls from db ->doc
            ArrayList<Document> urls = db.retriveURLs();
            HashMap<String, Token> invertedIndex = new HashMap<>();

            // Loop over them and call tokenize
            for (int i = 1; i < 3; i++) {
                String url = urls.get(i).getString("url");
                try {
                    org.jsoup.nodes.Document document = Jsoup.connect(url).get();

                    try {
                        indexer.tokenizeDocument(document, invertedIndex);
                        System.out.println(url);
                    } catch (Exception e) {
                        System.err.println("Error tokenizing document: " + e.getMessage());
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    System.err.println("Error fetching URL: " + e.getMessage());
                    e.printStackTrace();
                }

            }

            long prevTime = System.currentTimeMillis();

            db.insertIntoInvertedIndex(invertedIndex);
            long currTime = System.currentTimeMillis();

            long elapsedTime = currTime - prevTime;
            // System.out.println();
            System.out.println("Time taken to insert into db: " + elapsedTime + " ms");

            // Print for debugging issues
            for (String word : invertedIndex.keySet()) {
                Token token = invertedIndex.get(word);
                for (Posting posting : token.getPostings()) {
                    System.out.print("Word: " + word + " -> DocId: " +
                            posting.getDocID() + ", TF: " + posting.getTF() + ", Types: [");
                    // Print all positions
                    int i = 0;
                    for (String type : posting.getTypeCounts().keySet()) {
                        int count = posting.getTypeCounts().get(type);
                        System.out.print("{type: " + type + " ,count: " + count + "}");
                        if (i < posting.getTypeCounts().size() - 1) {
                            System.out.print(",");
                        }
                        i++;
                    }
                    System.out.println("]");
                }
            }
        } finally {
            // Always close the DB connection
            if (db != null) {
                db.close();
            }
        }
    }

    public static void insertIntoInvertedIndex(DBManager db, HashMap<String, Token> invertedIndex) {
        db.insertIntoInvertedIndex(invertedIndex);
    }
}