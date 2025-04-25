package com.searchengine.dbmanager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.ConnectionString;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.MongoInterruptedException;
import com.mongodb.MongoWriteException;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.model.Field;
import org.bson.conversions.Bson;
import com.searchengine.navi.indexer.Indexer.Token;
import com.searchengine.navi.indexer.Posting;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Accumulators.*;

public class DBManager {
    // Optional: Close the connection
    private volatile boolean isShuttingDown = false;
    private static final Logger logger = LoggerFactory.getLogger(DBManager.class);
    private MongoClient mongoClient;
    private MongoDatabase DB;
    // I changed it to invertedIndex instead of invertedIndexer
    private MongoCollection<Document> invertedIndexCollection; // Fixed typo
    private MongoCollection<Document> docCollection;
    private MongoCollection<Document> queriesCollection;

    public DBManager() {
        if (mongoClient == null) { // Ensure only one connection is created
            String connectionString = "mongodb+srv://esraa:navi123searchengine@cluster0.adp56.mongodb.net/";
            ServerApi serverApi = ServerApi.builder()
                    .version(ServerApiVersion.V1)
                    .build();

            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(connectionString))
                    .applyToSocketSettings(builder -> builder.connectTimeout(5, TimeUnit.SECONDS))
                    .serverApi(serverApi) // Added serverApi to settings
                    .build();

            try {
                mongoClient = MongoClients.create(settings);
                DB = mongoClient.getDatabase("navi");
                invertedIndexCollection = DB.getCollection("inverted index"); // Fixed typo
                queriesCollection = DB.getCollection("queries"); // Fixed typo
                docCollection = DB.getCollection("doc");

                System.out.println("✅ Successfully connected to MongoDB!\n\n\n");
            } catch (MongoException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Retrieves the _id of a document from the doc collection where url matches the
     * given value.
     * 
     * @param url The URL to search for
     * @return The _id as a String, or null if no document is found
     */
    public int retrieveDocID(String url) {
        try {
            Document filter = new Document("url", url);
            Document result = docCollection.find(filter)
                    .projection(new Document("id", 1)) // Fetch "id" field
                    .first();

            if (result != null) {
                Integer id = result.getInteger("id"); // Get "id" as Integer
                if (id != null) {
                    return id; // Return as int
                } else {
                    System.out.println("No 'id' field found for url: " + url);
                    return -1;
                }
            } else {
                System.out.println("No document found with url: " + url);
                return -1;
            }
        } catch (MongoException e) {
            System.err.println("Error retrieving document ID: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public List<Document> getDocumentsByID(List<Integer> ids) {
        return docCollection.find(Filters.in("id", ids)).into(new ArrayList<>());
    }

    public int getDocumentsCount() {
        try {
            int count = (int) docCollection.countDocuments();
            return count;
        } catch (MongoException e) {
            System.err.println("Error retrieving document ID: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public int getDF(String word) {
        try {
            Document filter = new Document("word", word);
            Document doc = invertedIndexCollection.find(filter).first();
            if (doc == null) {
                System.out.println("No document found with word: " + word);
                return -1;
            }
            List<?> array = doc.getList("postings", Object.class);
            int length = array.size();

            return length;
        } catch (MongoException e) {
            System.err.println("Error retrieving document ID: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public int getFieldLengthPerDoc(int docId, String field) {
        try {
            List<Bson> pipeline = Arrays.asList(
                    Aggregates.match(Filters.elemMatch("postings",
                            Filters.eq("docID", docId))),
                    Aggregates.unwind("$postings"),
                    Aggregates.match(Filters.eq("postings.docID", docId)),
                    Aggregates.group(null,
                            Accumulators.sum("total", "$postings.types." + field)));

            // Get the AggregateIterable
            // AggregateIterable<Document> aggregateIterable =
            // invertedIndexCollection.aggregate(pipeline);

            // Count the results by iterating
            // int length = 0;
            // for (Document doc : aggregateIterable) {
            // length++;
            // }

            // return length;
            Document result = invertedIndexCollection.aggregate(pipeline).first();
            return result != null ? result.getInteger("total", 0) : 0;
        } catch (MongoException e) {
            System.err.println("Error retrieving document ID: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public double getAvgFieldLength(String field) {
        try {
            AggregateIterable<Document> result = invertedIndexCollection.aggregate(Arrays.asList(
                    unwind("$postings"),
                    group(null, sum("total", "$postings.types." + field))));

            // Document result = invertedIndexCollection.aggregate(pipeline).first();
            // double count = result != null ? result.getInteger("totalCount") : 0L;
            Document doc = result.first();
            int count = doc != null ? doc.getInteger("total", 0) : 0;

            return count / getDocumentsCount();

        } catch (MongoException e) {
            System.err.println("Error retrieving document ID: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public List<Document> getWordPostings(String word) {
        try {
            List<Bson> pipeline = Arrays.asList(
                    Aggregates.match(Filters.eq("word", word)),
                    Aggregates.project(Projections.fields(
                            Projections.include("postings.docID", "postings.types"),
                            Projections.excludeId())));

            Document result = invertedIndexCollection.aggregate(pipeline).first();
            if (result != null) {
                return result.getList("postings", Document.class);
            }
        } catch (MongoException e) {
            System.err.println("Error retrieving document ID: " + e.getMessage());
            e.printStackTrace();

        }
        return new ArrayList<>();
    }

    // Retrieve all URLS in our db

    public ArrayList<Document> retriveURLs() {
        ArrayList<Document> urls = docCollection.find().projection(new Document("url", 1).append("_id", 0))
                .into(new ArrayList<>());

        return urls;
    }

    // Insert Inverted Index to the db

    // For now it is limited to 10 docs till we finalize our structure
    public void insertIntoInvertedIndex(HashMap<String, Token> invertedIndex) {
        List<WriteModel<Document>> bulkOperations = new ArrayList<>();
        int insertCount = 0;
        int updateCount = 0;

        // First, fetch all existing words to check what's already in the database
        Set<String> wordsToProcess = invertedIndex.keySet();
        MongoCursor<Document> existingDocs = invertedIndexCollection
                .find(new Document("word", new Document("$in", new ArrayList<>(wordsToProcess))))
                .iterator();

        // Create a map of existing words for quick lookup
        Map<String, Document> existingWordsMap = new HashMap<>();
        while (existingDocs.hasNext()) {
            Document doc = existingDocs.next();
            existingWordsMap.put(doc.getString("word"), doc);
        }

        // Process each word
        for (String word : wordsToProcess) {
            Token token = invertedIndex.get(word);
            ArrayList<Document> postingsList = new ArrayList<>();

            // Create a posting document for each Token
            for (Posting posting : token.getPostings()) {
                Map<String, Integer> typesMap = posting.getTypeCounts();
                Document postingDoc = new Document()
                        .append("docID", posting.getDocID())
                        .append("TF", posting.getTF())
                        .append("types", typesMap);

                postingsList.add(postingDoc);
            }

            // Check if the word already exists in the database
            if (existingWordsMap.containsKey(word)) {
                // Word exists, prepare update operation
                Document existingDoc = existingWordsMap.get(word);
                @SuppressWarnings("unchecked")
                List<Document> existingPostings = (List<Document>) existingDoc.get("postings");

                // Create a map of existing postings by docID for quick lookup
                Map<Integer, Document> docIdToPosting = new HashMap<>();
                for (Document existingPosting : existingPostings) {
                    docIdToPosting.put(existingPosting.getInteger("docID"), existingPosting);
                }

                // Process new postings - update existing or add new ones
                List<Document> updatedPostings = new ArrayList<>(existingPostings);
                for (Document newPosting : postingsList) {
                    int docId = newPosting.getInteger("docID");

                    if (docIdToPosting.containsKey(docId)) {
                        // Update existing posting
                        Document existingPosting = docIdToPosting.get(docId);
                        int existingTF = existingPosting.getInteger("TF");
                        int newTF = newPosting.getInteger("TF");

                        // Update TF
                        existingPosting.put("TF", existingTF + newTF);

                        // Merge type counts
                        @SuppressWarnings("unchecked")
                        Map<String, Integer> existingTypes = (Map<String, Integer>) existingPosting.get("types");
                        @SuppressWarnings("unchecked")
                        Map<String, Integer> newTypes = (Map<String, Integer>) newPosting.get("types");

                        for (String type : newTypes.keySet()) {
                            existingTypes.put(type, existingTypes.getOrDefault(type, 0) + newTypes.get(type));
                        }

                        existingPosting.put("types", existingTypes);
                    } else {
                        // Add new posting
                        updatedPostings.add(newPosting);
                    }
                }

                // Create update operation
                bulkOperations.add(
                        new UpdateOneModel<>(
                                new Document("word", word),
                                new Document("$set", new Document("postings", updatedPostings))));
                updateCount++;
            } else {
                // Word doesn't exist, prepare insert operation
                Document indexDoc = new Document()
                        .append("word", word)
                        .append("postings", postingsList);

                bulkOperations.add(new InsertOneModel<>(indexDoc));
                insertCount++;
            }

            // Execute bulk operations in batches to avoid memory issues
            if (bulkOperations.size() >= 1000) {
                executeBulkOperations(bulkOperations);
                bulkOperations.clear();
            }
        }

        // Execute any remaining operations
        if (!bulkOperations.isEmpty()) {
            executeBulkOperations(bulkOperations);
        }

        System.out.println("Indexing completed. Inserted: " + insertCount + ", Updated: " + updateCount + " documents");
    }

    private void executeBulkOperations(List<WriteModel<Document>> operations) {
        try {
            BulkWriteOptions options = new BulkWriteOptions().ordered(false);
            BulkWriteResult result = invertedIndexCollection.bulkWrite(operations, options);
            System.out.println("Bulk operation executed: " +
                    result.getInsertedCount() + " inserted, " +
                    result.getModifiedCount() + " modified");
        } catch (MongoBulkWriteException e) {
            System.err.println("Bulk write error: " + e.getMessage());
            // Process write errors if needed
            List<BulkWriteError> writeErrors = e.getWriteErrors();
            for (BulkWriteError error : writeErrors) {
                System.err.println("Error at index " + error.getIndex() + ": " + error.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Error during bulk write: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void getDocContentBy(String url, int id) {

    }

    private void insertSampleData() {
        try {

            // Insert the sample document
            Document sampleDoc = new Document()
                    .append("url", "https://toolsfairy.cot/sample-html-files#")
                    .append("title", "Free Sample HTML Files for Download, Test Web Pages")
                    .append("content", "Very Free Pages Online Tools Sampl…")
                    .append("id", 67);

            docCollection.insertOne(sampleDoc);
            System.out.println("Sample document inserted successfully.");
            sampleDoc = new Document()
                    .append("url", "https://toofairy.cot/sample-html-files#")
                    .append("title", "Free Sample HTML Files for Download, Test Web Pages")
                    .append("content", "Very Free new Pages Online Tools Sampl…")
                    .append("id", 68);

            docCollection.insertOne(sampleDoc);
            System.out.println("Sample document inserted successfully.");
        } catch (MongoException e) {
            System.err.println("Error inserting sample data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void insertSuggestion(String query) {
        if (query == null || query.trim().isEmpty()) {
            logger.warn("Attempted to insert empty or null suggestion");
            return;
        }

        String trimmedQuery = query.trim();
        try {
            // Check if the query already exists (case-insensitive match)
            Document filter = new Document("query", trimmedQuery);
            Document existingDoc = queriesCollection.find(filter).first();

            if (existingDoc == null) {
                // Query doesn't exist, insert it
                Document doc = new Document("query", trimmedQuery);
                queriesCollection.insertOne(doc);
                logger.info("Successfully inserted suggestion: {}", trimmedQuery);
            } else {
                logger.debug("Suggestion already exists: {}", trimmedQuery);
            }
        } catch (MongoException e) {
            logger.error("Error occurred while inserting suggestion '{}': {}", trimmedQuery, e.getMessage(), e);
        }
    }

    public List<String> getSuggestions(String queryTerms) {
        List<String> suggestions = new ArrayList<>();

        // Input validation
        if (queryTerms == null || queryTerms.trim().isEmpty()) {
            System.err.println("Received empty or null query for suggestions");
            return Collections.emptyList();
        }

        try {
            // Create a case-insensitive regex filter to match queries containing the
            // queryTerms
            Document filter = new Document("query", Pattern.compile(Pattern.quote(queryTerms.trim()),
                    Pattern.CASE_INSENSITIVE));
            queriesCollection.find(filter)
                    .limit(5) // Default limit
                    .forEach(doc -> suggestions.add(doc.getString("query")));

            return suggestions;
        } catch (MongoException e) {
            System.err.println("Error occurred while fetching suggestions for query ");
            return Collections.emptyList();
        }
    }

    public List<Document> getDocuments(List<ObjectId> docsID) {
        return docCollection.find().into(new ArrayList<>());
    }

    public void close() {
        try {
            // Tell any monitoring code you're in shutdown process
            isShuttingDown = true;

            if (mongoClient != null) {
                // Stop any ongoing operations
                try {
                    // Give background operations time to complete naturally
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                    // Ignore if interrupted during sleep
                }

                // Get the current thread group
                ThreadGroup group = Thread.currentThread().getThreadGroup();
                // Estimate thread count
                int estimatedSize = group.activeCount() * 2;
                Thread[] threads = new Thread[estimatedSize];
                // Fill our array with active threads
                int actualSize = group.enumerate(threads);

                // Find and interrupt MongoDB monitoring threads before closing
                for (int i = 0; i < actualSize; i++) {
                    Thread thread = threads[i];
                    String threadName = thread.getName();
                    // Look for MongoDB driver threads
                    if (threadName.contains("cluster-") && threadName.contains("mongodb")) {
                        try {
                            // Interrupt these threads explicitly
                            thread.interrupt();
                        } catch (Exception ignored) {
                            // Ignore any errors from this attempt
                        }
                    }
                }

                // Now close the client
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    mongoClient.close();
                }));
                System.out.println("MongoDB connection closed successfully.");
            }
        } catch (Exception e) {
            System.err.println("Error during MongoDB connection closure: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Initialize DBManager
        DBManager dbManager = new DBManager();

        // Close the connection
        dbManager.close();
    }
}