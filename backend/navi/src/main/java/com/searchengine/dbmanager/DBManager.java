package com.searchengine.dbmanager;

import java.lang.reflect.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators.In;

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
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
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
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Accumulators.*;
import com.searchengine.navi.indexer.Indexer.Token;
import com.searchengine.navi.indexer.Posting;

public class DBManager {
    private static final Logger logger = LoggerFactory.getLogger(DBManager.class);
    private MongoClient mongoClient;
    private MongoDatabase DB;
    // I changed it to invertedIndex instead of invertedIndexer
    private MongoCollection<Document> invertedIndexCollection; // Fixed typo
    private MongoCollection<Document> docCollection;
    private MongoCollection<Document> queriesCollection;
    private boolean isShuttingDown;

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

    public HashMap<String, Integer> getDFs(List<String> words) {
        HashMap<String, Integer> dfMap = new HashMap<>();

        // Initialize map with -1 for all words
        for (String word : words) {
            dfMap.put(word, -1);
        }

        try {
            Iterable<Document> documents = invertedIndexCollection.find(
                    Filters.in("word", words));

            // Process each document
            for (Document doc : documents) {
                String word = doc.getString("word");
                List<?> postings = doc.getList("postings", Object.class);
                if (postings != null) {
                    dfMap.put(word, postings.size());
                } else {
                    logger.warn("No postings found for word: {}", word);
                }
            }
        } catch (MongoException e) {
            logger.error("Error retrieving DFs: {}", e.getMessage(), e);
        }

        return dfMap;
    }

    public Map<Integer, Map<String, Integer>> getFieldOccurrencesForDocs(List<Integer> docIds) {
        Map<Integer, Map<String, Integer>> result = new HashMap<>();
        String fields[] = { "h1", "h2", "a", "other" };

        // Aggregation pipeline
        List<Bson> pipeline = Arrays.asList(
                // Unwind the postings array
                Aggregates.unwind("$postings"),
                // Match postings where docID is in the input list
                Aggregates.match(Filters.in("postings.docID", docIds)),
                // Project to extract docID and types
                Aggregates.project(new Document("_id", 0)
                        .append("docID", "$postings.docID")
                        .append("types", "$postings.types")),
                // Convert types to an array of key-value pairs using $objectToArray
                Aggregates.addFields(
                        new Field<>("types", new Document("$objectToArray", "$types"))),
                // Unwind the types array
                Aggregates.unwind("$types"),
                // Group by docID and field name, summing the frequencies
                Aggregates.group(
                        new Document("docID", "$docID").append("field", "$types.k"),
                        Accumulators.sum("total", "$types.v")));

        // Execute aggregation
        try {
            for (Document doc : invertedIndexCollection.aggregate(pipeline)) {
                Document id = (Document) doc.get("_id");
                int docId = id.getInteger("docID");
                String field = id.getString("field");
                int total = doc.getInteger("total");

                result.computeIfAbsent(docId, k -> new HashMap<>()).put(field, total);
            }
        } catch (Exception e) {
            System.out.println("Error in aggregation: " + e.getMessage());
        }

        // Ensure all input docIds are in the result, even if they have no postings
        for (Integer docId : docIds) {
            Map<String, Integer> fieldOccurrences = result.computeIfAbsent(docId, k -> new HashMap<>());
            for (String field : fields) {
                fieldOccurrences.putIfAbsent(field, 0);
            }
        }

        return result;
    }

    public HashMap<String, Integer> getAllFieldsCount() {
        HashMap<String, Integer> fieldCounts = new HashMap<>();
        try {
            Iterable<Document> results = invertedIndexCollection.aggregate(Arrays.asList(
                    new Document("$unwind", "$postings"),
                    new Document("$project", new Document("types", "$postings.types")),
                    new Document("$addFields", new Document("typesArray", new Document("$objectToArray", "$types"))),
                    new Document("$unwind", "$typesArray"),
                    new Document("$match",
                            new Document("typesArray.k", new Document("$in", Arrays.asList("h1", "h2", "a", "other")))),
                    new Document("$group", new Document("_id", "$typesArray.k")
                            .append("total", new Document("$sum", "$typesArray.v")))));

            // Process the results into a map
            for (Document result : results) {
                String field = result.getString("_id");
                Integer total = result.getInteger("total");
                if (field != null && total != null) {
                    fieldCounts.put(field, total);
                }
            }
        } catch (MongoException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        return fieldCounts;
    }

    public HashMap<String, List<Document>> getWordsPostings(List<String> words) {
        HashMap<String, List<Document>> termPostings = new HashMap<>();
        try {
            // Query all words in bulk using $in
            Iterable<Document> documents = invertedIndexCollection.find(
                    Filters.in("word", words));
            for (Document doc : documents) {
                String term = doc.getString("word");
                List<Document> postings = doc.getList("postings", Document.class);
                termPostings.put(term, postings != null ? postings : Collections.emptyList());
            }
        } catch (MongoException e) {
            System.err.println("Error retrieving postings: " + e.getMessage());
            e.printStackTrace();
        }
        // Ensure all words are in the result, even if they have no postings
        for (String word : words) {
            termPostings.putIfAbsent(word, Collections.emptyList());
        }
        return termPostings;
    }

    public HashMap<Integer, Double> getPageRanks(List<Integer> docIds) {
        HashMap<Integer, Double> docRanks = new HashMap<>();

        try {
            docCollection.find(Filters.in("id", docIds))
                    .forEach(document -> {
                        Integer id = document.getInteger("id");
                        Double rank = document.getDouble("rank");
                        if (id != null && rank != null) {
                            docRanks.put(id, rank);
                        }
                    });
        } catch (MongoException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }

        return docRanks;
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

    // Fetch all content when is_indexed is false

    public FindIterable<Document> getUnindexedDocuments() {
        Document filter = new Document("isIndexed", false);
        Document projection = new Document("content", 1).append("h1", 1).append("h2", 1).append("children", 1).append(
                "url",
                1);
        FindIterable<Document> result = docCollection.find(filter).projection(projection);

        if (result != null) {
            return result;
        } else {
            System.out.println("No unindexed documents found.");
            return null;
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

    // For phrase matching
    public ArrayList<Document> getDocumentsContent() {
        ArrayList<Document> docs = docCollection.find()
                .projection(new Document("content", 1).append("url", 1).append("id", 1))
                .into(new ArrayList<>());

        return docs;
    }

    // Optional: Close the connection
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
        FindIterable<Document> urls = dbManager.getUnindexedDocuments();
        for (Document doc : urls) {
            System.out.println(doc);
        }

        // Close the connection
        dbManager.close();
    }
}