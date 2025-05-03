package com.searchengine.navi.indexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bson.Document;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import com.searchengine.dbmanager.DBManager;
import com.searchengine.navi.indexer.Indexer.Token;

public class Main {
    static DBManager db = new DBManager();
    static Indexer indexer = new Indexer();
    static final int BATCH_SIZE = 50; // Adjust based on testing (e.g., 100-1000 documents)

    public static void main(String[] args) throws IOException {
        long startTime = System.currentTimeMillis();
        processDocumentsInBatches();
        long endTime = System.currentTimeMillis();
        System.out.println("Total indexing time: " + (endTime - startTime) + " ms");
    }

    private static void processDocumentsInBatches() {
        // Fetch unindexed documents
        FindIterable<Document> docs = db.getUnindexedDocuments();
        MongoCursor<Document> cursor = docs.iterator();
        List<Document> batch = new ArrayList<>();
        HashMap<String, Token> invertedIndex = new HashMap<>();
        int batchCount = 0;

        try {
            while (cursor.hasNext()) {
                batch.add(cursor.next());

                // Process batch when it reaches BATCH_SIZE or no more documents
                if (batch.size() >= BATCH_SIZE || !cursor.hasNext()) {
                    batchCount++;
                    System.out.println("Processing batch " + batchCount + " with " + batch.size() + " documents");

                    // Tokenize documents in the batch
                    long tokenizeStart = System.currentTimeMillis();
                    for (Document doc : batch) {
                        try {
                            indexer.tokenizeDocument(doc, invertedIndex);
                            // Mark document as indexed (optional: add lastIndexed timestamp)
                            // db.markDocumentAsIndexed(doc.getString("url"));
                        } catch (Exception e) {
                            System.err.println(
                                    "Error tokenizing document: " + doc.getString("url") + " - " + e.getMessage());
                        }
                    }
                    long tokenizeEnd = System.currentTimeMillis();
                    System.out.println(
                            "Tokenization time for batch " + batchCount + ": " + (tokenizeEnd - tokenizeStart) + " ms");

                    // Insert inverted index for the batch
                    long insertStart = System.currentTimeMillis();
                    db.insertIntoInvertedIndex(invertedIndex);
                    long insertEnd = System.currentTimeMillis();
                    System.out.println(
                            "Database insert time for batch " + batchCount + ": " + (insertEnd - insertStart) + " ms");

                    // Clear the inverted index and batch for the next iteration
                    invertedIndex.clear();
                    batch.clear();
                }
            }
        } finally {
            cursor.close();
        }
    }
}