package com.searchengine.navi.indexer;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bson.Document;

import com.google.gson.Gson;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import com.searchengine.dbmanager.DBManager;
import com.searchengine.navi.indexer.Indexer.Token;

public class Main {
    static DBManager db = new DBManager();
    static Indexer indexer = new Indexer();
    static final int BATCH_SIZE = 10; // Adjust based on testing
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(Main.class.getName());

    static String FIELD_COUNTS_PATH = "field_counts.json"; // JSON file to store fields lengths

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        try {
            processDocumentsInBatches();
        } catch (IOException e) {
            logger.severe("IOException in main: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            logger.severe("Unexpected error in main: " + e.getMessage());
            e.printStackTrace();
        }
        long endTime = System.currentTimeMillis();
        logger.info("Total indexing time: " + (endTime - startTime) + " ms");
    }

    private static void storeFieldCounts() {
        HashMap<String, Integer> avgFieldCounts = new HashMap<>();
        Gson gson = new Gson();

        avgFieldCounts = db.getAllFieldsCount();
        try (FileWriter writer = new FileWriter(FIELD_COUNTS_PATH)) {
            gson.toJson(avgFieldCounts, writer);
        } catch (IOException e) {
            System.err.println("Failed to save averages: " + e.getMessage());
        }
    }

    private static void processDocumentsInBatches() throws IOException {
        FindIterable<Document> docs = db.getUnindexedDocuments();
        MongoCursor<Document> cursor = docs.iterator();
        List<Document> batch = new ArrayList<>();
        HashMap<String, Token> invertedIndex = new HashMap<>();
        int batchCount = 0;

        try {
            while (true) {
                try {
                    if (!cursor.hasNext())
                        break;
                    Document doc = cursor.next();
                    batch.add(doc);
                    logger.info("Added document to batch " + batchCount + ": " + doc.getString("url"));

                    if (batch.size() >= BATCH_SIZE || !cursor.hasNext()) {
                        batchCount++;
                        logger.info("Processing batch " + batchCount + " with " + batch.size() + " documents");

                        long tokenizeStart = System.currentTimeMillis();
                        for (Document document : batch) {
                            try {
                                logger.info("Tokenizing document: " + document.getString("url"));
                                indexer.tokenizeDocument(document, invertedIndex);
                            } catch (Exception e) {
                                logger.severe("Error tokenizing document " + document.getString("url") + ": "
                                        + e.getMessage());
                                e.printStackTrace();
                                continue; // Skip to next document on failure
                            }
                        }
                        long tokenizeEnd = System.currentTimeMillis();
                        logger.info("Tokenization time for batch " + batchCount + ": " + (tokenizeEnd - tokenizeStart)
                                + " ms");
                        logger.info("InvertedIndex size before insert: " + invertedIndex.size());

                        long insertStart = System.currentTimeMillis();
                        try {
                            logger.info("Inserting inverted index for batch " + batchCount);
                            db.insertIntoInvertedIndex(invertedIndex);
                            // Mark all documents in the batch as indexed after successful insert
                            for (Document document : batch) {
                                try {
                                    db.markDocumentAsIndexed(document.getString("url"));
                                } catch (Exception e) {
                                    logger.severe("Error marking document as indexed for URL "
                                            + document.getString("url") + ": " + e.getMessage());
                                    e.printStackTrace();
                                }
                            }
                        } catch (Exception e) {
                            logger.severe("Error inserting into inverted index for batch " + batchCount + ": "
                                    + e.getMessage());
                            e.printStackTrace();
                            continue; // Skip to next batch on failure
                        }
                        long insertEnd = System.currentTimeMillis();
                        logger.info("Database insert time for batch " + batchCount + ": " + (insertEnd - insertStart)
                                + " ms");

                        logger.info("InvertedIndex size before clear: " + invertedIndex.size());
                        invertedIndex.clear();
                        logger.info("InvertedIndex size after clear: " + invertedIndex.size());

                        batch.clear();
                    }
                } catch (com.mongodb.MongoCursorNotFoundException e) {
                    logger.warning("Cursor expired for batch " + batchCount + ": " + e.getMessage());
                    logger.info("Attempting to restart cursor or skip batch");
                    break;
                }
            }
        } finally {
            if (cursor != null)
                cursor.close();
            logger.info("Cursor closed. Batch processing completed.");
            storeFieldCounts();
        }
    }
}