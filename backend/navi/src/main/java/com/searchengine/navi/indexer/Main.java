package com.searchengine.navi.indexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.jsoup.Jsoup;
import com.searchengine.dbmanager.DBManager;
import com.searchengine.navi.indexer.Indexer.Token;

public class Main {
    // Use ConcurrentHashMap for thread safety
    private static ConcurrentHashMap<String, Token> invertedIndex = new ConcurrentHashMap<>();
    // Counter for processed documents
    private static AtomicInteger processedCount = new AtomicInteger(0);
    
    public static void main(String[] args) {
        // Create a single DBManager instance
        DBManager db = new DBManager();
        Indexer indexer = new Indexer(); // Pass DB reference to indexer
        
        try {
            // Retrieve urls from db ->doc
            ArrayList<Document> urls = db.retriveURLs();
            int totalUrls = urls.size();
            System.out.println("Total URLs to process: " + totalUrls);
            
            // Determine number of threads based on available processors
            int numThreads = Runtime.getRuntime().availableProcessors();
            System.out.println("Using " + numThreads + " threads for processing");
            
            // Create thread pool
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            
            // Submit tasks to the thread pool
            for (int i = 0; i < totalUrls; i++) {
                final int urlIndex = i; 
                executor.submit(() -> processDocument(urls.get(urlIndex), indexer));
            }
            
            // Shutdown the executor and wait for all tasks to complete
            executor.shutdown();
            
            try {
                // Wait for all tasks to complete or timeout after 10 minutes
                if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                    System.err.println("Timeout occurred while waiting for tasks to complete");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                System.err.println("Thread execution interrupted: " + e.getMessage());
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            System.out.println("All documents processed. Inserting into database...");
            
            // Measure database insertion time
            long prevTime = System.currentTimeMillis();
            db.insertIntoInvertedIndex(invertedIndex);
            long currTime = System.currentTimeMillis();
            
            long elapsedTime = currTime - prevTime;
            System.out.println("Time taken to insert into db: " + elapsedTime + " ms");
            
            // Print for debugging issues
            // printDebugInfo();
            
        } finally {
            // Always close the DB connection
            if (db != null) {
                db.close();
            }
        }
    }
    
    private static void processDocument(Document urlDoc, Indexer indexer) {
        String url = urlDoc.getString("url");
        try {
            org.jsoup.nodes.Document document = Jsoup.connect(url).get();
            
            try {
                // Synchronize on indexer to avoid concurrent modification issues
                synchronized (indexer) {
                    indexer.tokenizeDocument(document, invertedIndex);
                }
                
                int count = processedCount.incrementAndGet();
                System.out.println("[Thread " + Thread.currentThread().getId() + "] Processed URL (" + count + "): " + url);
            } catch (Exception e) {
                System.err.println("[Thread " + Thread.currentThread().getId() + "] Error tokenizing document: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (IOException e) {
            System.err.println("[Thread " + Thread.currentThread().getId() + "] Error fetching URL: " + url + " - " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void printDebugInfo() {
        System.out.println("\n--- Debug Information ---");
        System.out.println("Total unique words in index: " + invertedIndex.size());
        
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
    }
    
    public static void insertIntoInvertedIndex(DBManager db, ConcurrentHashMap<String, Token> invertedIndex) {
        db.insertIntoInvertedIndex(invertedIndex);
    }
}