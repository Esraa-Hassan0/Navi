package com.searchengine.dbmanager;

import java.lang.reflect.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators.In;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.client.model.Field;
import org.bson.conversions.Bson;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Accumulators.*;

import com.searchengine.navi.crawler.Url;
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
    private static final int BATCH_SIZE = 200;

    public DBManager() {
        if (mongoClient == null) { // Ensure only one connection is created
            String connectionString = "mongodb://localhost:27017/";
            // String connectionString =
            // "mongodb+srv://esraa:navi123searchengine@cluster0.adp56.mongodb.net/";
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
                try {
                    ListIndexesIterable<Document> indexes = docCollection.listIndexes();
                    boolean urlIndexExists = false;
                    for (Document index : indexes) {
                        String indexName = index.getString("name");
                        if ("url_1".equals(indexName)) {
                            urlIndexExists = true;
                            Document key = index.get("key", Document.class);
                            if (key != null && key.containsKey("url") && !index.containsKey("unique")) {
                                docCollection.dropIndex("url_1");
                                docCollection.createIndex(Indexes.ascending("url"), new IndexOptions().unique(true));
                                logger.info("Recreated unique index on 'url'");
                            }
                            break;
                        }
                    }
                    if (!urlIndexExists) {
                        docCollection.createIndex(Indexes.ascending("url"), new IndexOptions().unique(true));
                        logger.info("Created unique index on 'url'");
                    }
                    docCollection.createIndex(Indexes.ascending("hashingDoc"));
                    docCollection.createIndex(Indexes.ascending("children"));
                    docCollection.createIndex(Indexes.ascending("parents"));
                    docCollection.createIndex(Indexes.ascending("rank"));
                } catch (MongoException e) {
                    logger.warn("Failed to create index: {}, proceeding without it", e.getMessage());
                }
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
    public ObjectId retrieveDocID(String url) {
        try {
            Document filter = new Document("url", url);
            Document result = docCollection.find(filter)
                    .projection(new Document("i_d", 1)) // Fetch "id" field
                    .first();

            if (result != null) {
                ObjectId id = result.getObjectId("_id"); // Get "id" as Integer
                if (id != null) {
                    return id; // Return as int
                } else {
                    System.out.println("No 'id' field found for url: " + url);
                    return null;
                }
            } else {
                System.out.println("No document found with url: " + url);
                return null;
            }
        } catch (MongoException e) {
            System.err.println("Error retrieving document ID: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public List<Document> getDocumentsByIDOrdered(List<ObjectId> ids) {
        // Fetch all documents with the given IDs
        List<Document> fetchedDocs = docCollection.find(Filters.in("_id", ids)).into(new ArrayList<>());

        // Create a lookup map from ObjectId to Document
        Map<ObjectId, Document> docMap = fetchedDocs.stream()
                .collect(Collectors.toMap(doc -> doc.getObjectId("_id"), Function.identity()));

        // Preserve the input order
        return ids.stream()
                .map(docMap::get) // Lookup each ID in the original order
                .filter(Objects::nonNull) // Optionally remove missing documents
                .collect(Collectors.toList());
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

    public Map<ObjectId, Map<String, Integer>> getFieldOccurrencesForDocs(List<ObjectId> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // Use unmodifiable set for fields (immutable, thread-safe)
        final Set<String> fields = Set.of("h1", "h2", "a", "other");
        // Pre-size the result map to avoid resizing
        Map<ObjectId, Map<String, Integer>> result = new HashMap<>(docIds.size());

        // Initialize default field map to avoid repeated creation
        Map<String, Integer> defaultFieldMap = fields.stream()
                .collect(Collectors.toMap(
                        field -> field,
                        field -> 0,
                        (a, b) -> a,
                        () -> new HashMap<>(fields.size())));

        // Optimize pipeline with projection to reduce data transfer
        List<Bson> pipeline = List.of(
                Aggregates.unwind("$postings"),
                Aggregates.match(Filters.in("postings.docID", docIds)),
                Aggregates.group(
                        "$postings.docID",
                        Accumulators.sum("h1", new Document("$ifNull", List.of("$postings.types.h1", 0))),
                        Accumulators.sum("h2", new Document("$ifNull", List.of("$postings.types.h2", 0))),
                        Accumulators.sum("a", new Document("$ifNull", List.of("$postings.types.a", 0))),
                        Accumulators.sum("other", new Document("$ifNull", List.of("$postings.types.other", 0)))),
                Aggregates.project(new Document("_id", 1)
                        .append("h1", 1)
                        .append("h2", 1)
                        .append("a", 1)
                        .append("other", 1)));

        try (MongoCursor<Document> cursor = invertedIndexCollection
                .aggregate(pipeline)
                .batchSize(1000)
                .allowDiskUse(true) // Allow disk use for large datasets
                .iterator()) {

            while (cursor.hasNext()) {
                Document doc = cursor.next();
                ObjectId docId = doc.getObjectId("_id");

                // Create field map only once per document
                Map<String, Integer> fieldMap = new HashMap<>(fields.size());
                fields.forEach(field -> fieldMap.put(field, doc.getInteger(field, 0)));

                result.put(docId, fieldMap);
            }
        } catch (MongoException e) {
            // Log error properly instead of printing to stderr
            logger.error("Aggregation failed for docIds: {}", docIds, e);
            return Collections.emptyMap(); // Or throw a custom exception
        }

        // Ensure all input docIds are represented efficiently
        docIds.forEach(docId -> result.computeIfAbsent(docId, k -> new HashMap<>(defaultFieldMap)));

        return result;
    }

    public HashMap<ObjectId, Map<String, Integer>> getFieldLengths(List<ObjectId> ids) {

        // HashMap for field counts: ObjectId -> {fieldName -> count}
        HashMap<ObjectId, Map<String, Integer>> fieldLengths = new HashMap<>();

        try {
            docCollection.find(Filters.in("_id", ids))
                    .forEach(document -> {
                        ObjectId id = document.getObjectId("_id");
                        if (id != null) {
                            Map<String, Integer> counts = new HashMap<>();
                            counts.put("a", document.getInteger("aCount", 0));
                            counts.put("h1", document.getInteger("h1Count", 0));
                            counts.put("h2", document.getInteger("h2Count", 0));
                            counts.put("other", document.getInteger("otherCount", 0));
                            fieldLengths.put(id, counts);
                        }
                    });
        } catch (MongoException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }

        return fieldLengths;
    }

    public HashMap<String, Integer> getAllFieldsCount() {
        HashMap<String, Integer> fieldCounts = new HashMap<>();
        List<String> fields = Arrays.asList("h1", "h2", "a", "other");
        for (String field : fields) {
            fieldCounts.put(field, 0);
        }

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
            // Query all words in bulk using
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

    public HashMap<ObjectId, Double> getPageRanks(List<ObjectId> docIds) {
        HashMap<ObjectId, Double> docRanks = new HashMap<>();

        try {
            docCollection.find(Filters.in("_id", docIds))
                    .forEach(document -> {
                        ObjectId id = document.getObjectId("_id");
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
    public void appendTagCounts(ObjectId id, int h1Count, int h2Count,
            int aCount, int otherCount) {
        docCollection.updateOne(
                Filters.eq("_id", id),
                Updates.combine(
                        Updates.set("h1Count", h1Count),
                        Updates.set("h2Count", h2Count),
                        Updates.set("ACount", aCount),
                        Updates.set("otherCount", otherCount)));
    }

    public void insertIntoInvertedIndex(HashMap<String, Token> invertedIndex) {
        List<WriteModel<Document>> bulkOperations = new ArrayList<>();
        int updateCount = 0;

        for (Map.Entry<String, Token> entry : invertedIndex.entrySet()) {
            String word = entry.getKey();
            Token token = entry.getValue();

            for (Posting posting : token.getPostings()) {
                Map<String, Integer> typesMap = posting.getTypeCounts();
                Document postingDoc = new Document()
                        .append("docID", posting.getDocID())
                        // .append("TF", posting.getTF()) //No Need
                        .append("types", typesMap);

                // Check if a posting for this docID already exists
                bulkOperations.add(
                        new UpdateOneModel<>(
                                new Document("word", word)
                                        .append("postings.docID", new Document("$ne", posting.getDocID())),
                                new Document("$push", new Document("postings", postingDoc)),
                                new UpdateOptions().upsert(true)));

                // If the docID exists, update its TF and types
                bulkOperations.add(
                        new UpdateOneModel<>(
                                new Document("word", word)
                                        .append("postings.docID", posting.getDocID()),
                                new Document("$set", new Document("postings.$", postingDoc)),
                                new UpdateOptions().upsert(true)));
                updateCount++;
            }

            if (bulkOperations.size() >= 500) { // Process in batches of 500
                executeBulkOperations(bulkOperations);
                bulkOperations.clear();
            }
        }

        if (!bulkOperations.isEmpty()) {
            executeBulkOperations(bulkOperations);
        }

        System.out.println("Indexing completed. Updated: " + updateCount + " documents");
    }

    private void executeBulkOperations(List<WriteModel<Document>> operations) {
        try {
            BulkWriteOptions options = new BulkWriteOptions().ordered(false);
            BulkWriteResult result = invertedIndexCollection.bulkWrite(operations, options);
            System.out.println("Bulk operation executed: " +
                    result.getInsertedCount() + " inserted, " +
                    result.getModifiedCount() + " modified, " +
                    result.getUpserts().size() + " upserted");
        } catch (MongoBulkWriteException e) {
            System.err.println("Bulk write error: " + e.getMessage());
            e.getWriteErrors().forEach(
                    error -> System.err.println("Error at index " + error.getIndex() + ": " + error.getMessage()));
            // Optionally skip failed operations and continue
        } catch (Exception e) {
            System.err.println("Error during bulk write: " + e.getMessage());
            e.printStackTrace();
        }
    }
    // Fetch all content when is_indexed is false

    public FindIterable<Document> getUnindexedDocuments() {
        Document filter = new Document("isIndexed", false);
        Document projection = new Document("content", 1).append("h1", 1).append("h2", 1).append("a", 1).append(
                "url",
                1).append("title", 1);
        FindIterable<Document> result = docCollection.find(filter).projection(projection);

        if (result != null) {
            return result;
        } else {
            System.out.println("No unindexed documents found.");
            return null;
        }

    }

    /**
     * Retrieves document content by URL or ID
     * 
     * @param url The URL of the document to retrieve
     * @param id  The ID of the document to retrieve (used if URL is null or empty)
     * @return Document containing the content or null if not found
     */
    public String getDocContentByURL(String url) {
        try {
            Document filter = null;

            // Determine which filter to use based on provided parameters
            if (url != null && !url.trim().isEmpty()) {
                // If URL is provided, use it as the primary search criteria
                filter = new Document("url", url);
            }

            // Define which fields to retrieve
            Document projection = new Document()
                    .append("content", 1);

            // Find and return the document
            Document result = docCollection.find(filter)
                    .projection(projection)
                    .first();

            if (result != null) {
                return result.getString("content");
            } else {
                logger.warn("No document found with {} {}");
                return null;
            }
        } catch (MongoException e) {
            logger.error("Error retrieving document content: {}", e.getMessage(), e);
            return null;
        }
    }

    public String getDocAnchorById(String url) {
        try {
            Document filter = null;

            // Determine which filter to use based on provided parameters
            if (url != null && !url.trim().isEmpty()) {
                // If URL is provided, use it as the primary search criteria
                filter = new Document("url", url);
            }

            // Define which fields to retrieve
            Document projection = new Document()
                    .append("a", 1);

            // Find and return the document
            Document result = docCollection.find(filter)
                    .projection(projection)
                    .first();

            if (result != null) {
                return result.getString("a");
            } else {
                logger.warn("No document found with {} {}");
                return null;
            }
        } catch (MongoException e) {
            logger.error("Error retrieving document a: {}", e.getMessage(), e);
            return null;
        }
    }

    public String getDocH1ById(String url) {
        try {
            Document filter = null;

            // Determine which filter to use based on provided parameters
            if (url != null && !url.trim().isEmpty()) {
                // If URL is provided, use it as the primary search criteria
                filter = new Document("url", url);
            }

            // Define which fields to retrieve
            Document projection = new Document()
                    .append("h1", 1);

            // Find and return the document
            Document result = docCollection.find(filter)
                    .projection(projection)
                    .first();

            if (result != null) {
                return result.getString("h1");
            } else {
                logger.warn("No document found with {} {}");
                return null;
            }
        } catch (MongoException e) {
            logger.error("Error retrieving document h1: {}", e.getMessage(), e);
            return null;
        }
    }

    public String getDocH2ById(String url) {
        try {
            Document filter = null;

            // Determine which filter to use based on provided parameters
            if (url != null && !url.trim().isEmpty()) {
                // If URL is provided, use it as the primary search criteria
                filter = new Document("url", url);
            }

            // Define which fields to retrieve
            Document projection = new Document()
                    .append("h2", 1);

            // Find and return the document
            Document result = docCollection.find(filter)
                    .projection(projection)
                    .first();

            if (result != null) {
                return result.getString("h2");
            } else {
                logger.warn("No document found with {} {}");
                return null;
            }
        } catch (MongoException e) {
            logger.error("Error retrieving document h2: {}", e.getMessage(), e);
            return null;
        }
    }

    public String getDocTitleById(String url) {
        try {
            Document filter = null;

            // Determine which filter to use based on provided parameters
            if (url != null && !url.trim().isEmpty()) {
                // If URL is provided, use it as the primary search criteria
                filter = new Document("url", url);
            }

            // Define which fields to retrieve
            Document projection = new Document()
                    .append("title", 1);

            // Find and return the document
            Document result = docCollection.find(filter)
                    .projection(projection)
                    .first();

            if (result != null) {
                return result.getString("title");
            } else {
                logger.warn("No document found with {} {}");
                return null;
            }
        } catch (MongoException e) {
            logger.error("Error retrieving document title: {}", e.getMessage(), e);
            return null;
        }
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
        ArrayList<Document> docs = new ArrayList<>();

        FindIterable<Document> cursor = docCollection.find()
                .projection(new Document("content", 1)
                        .append("url", 1)
                        .append("h1", 1)
                        .append("h2", 1)
                        .append("a", 1))
                .batchSize(1000); // Set batch size to 1000 documents per batch

        try (MongoCursor<Document> iterator = cursor.iterator()) {
            while (iterator.hasNext()) {
                docs.add(iterator.next());
            }
        }

        return docs;
    }

    public ArrayList<Document> getDocumentsContainingPhrase(String phrase) {

        // Create regex filters for each field
        Bson filter = Filters.or(
                Filters.regex("content",
                        phrase, "i"),
                Filters.regex("h1",
                        phrase, "i"),
                Filters.regex("h2",
                        phrase, "i"),
                Filters.regex("a", phrase, "i"),
                Filters.regex("title", phrase, "i"));

        // Execute query with debugging
        ArrayList<Document> docs = docCollection.find(filter)
                .projection(new Document("content", 1)
                        .append("url", 1)
                        .append("h1", 1)
                        .append("h2", 1)
                        .append("a", 1).append("title", 1))
                .batchSize(1000)
                .into(new ArrayList<>());

        System.out.println("Retrieved " + docs.size() + " documents for phrase: " + phrase);
        return docs;
    }

    public HashSet<ObjectId> getAllDocumentIds() {
        HashSet<ObjectId> allDocIds = new HashSet<>();
        for (Document doc : docCollection.find().projection(new Document("_id", 1))) {
            ObjectId docId = doc.getObjectId("_id");
            if (docId != null) {
                allDocIds.add(docId);
            }
        }
        System.out.println("Total documents in database: " + allDocIds.size());
        return allDocIds;
    }

    public void markDocumentAsIndexed(String url) {
        try {
            Document query = new Document("url", url);
            Document update = new Document("$set", new Document("isIndexed", true));
            docCollection.updateOne(query, update);
            System.out.println("Marked document as indexed: " + url);
        } catch (Exception e) {
            System.err.println("Error marking document as indexed for URL " + url + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public long resetIndexedStatus() {
        try {
            // Use updateMany to set isIndexed to false for all documents
            Document filter = new Document(); // Empty filter matches all documents
            Document update = new Document("$set", new Document("isIndexed", false));
            UpdateOptions options = new UpdateOptions().upsert(false); // No upsert needed

            UpdateResult result = docCollection.updateMany(filter, update, options);
            long modifiedCount = result.getModifiedCount();

            System.out.println("Reset isIndexed to false for " + modifiedCount + " documents.");
            return modifiedCount;
        } catch (Exception e) {
            System.err.println("Error resetting isIndexed status: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
    public void insertBatch(List<Url> urls) {
        try {
            List<Document> documents = urls.stream().map(url -> new Document()
                    .append("url", url.getUrl())
                    .append("title", url.getTitle())
                    .append("content", url.getContent())
                    .append("hashingDoc", url.getHashingDoc())
                    // .append("urlsIn", url.getUrlsIn())
                    // .append("urlsOut", url.getUrlsOut())
                    .append("baseUrl", url.getBaseUrl())
                    .append("parents", url.getParents())
                    .append("children", url.getChildren())
                    .append("isIndexed", url.isIndexed())
                    .append("lastTime", url.getLastTime())
                    .append("rank", url.getRank())
                    .append("a", url.getChildTXt())
                    .append("h1", url.getH1())
                    .append("h2", url.getH2())
                    .append("etag", url.getEtag())
                    .append("lastModified", url.getLastModified())).collect(Collectors.toList());
            docCollection.insertMany(documents);
            logger.info("Successfully inserted {} documents into MongoDB.", documents.size());
        } catch (MongoException e) {
            logger.error("Failed to insert batch: {}", e.getMessage());
            throw new RuntimeException("Failed to insert batch into MongoDB", e);
        }
    }

    // public void updateBatch(List<Url> urls) {
    //     try {
    //         int updatedCount = 0;
    //         for (Url url : urls) {
    //             Bson filter = Filters.eq("url", url.getUrl());
    //             Bson update = Updates.combine(
    //                     Updates.set("title", url.getTitle()),
    //                     Updates.set("content", url.getContent()),
    //                     Updates.set("hashingDoc", url.getHashingDoc()),
    //                     Updates.set("urlsIn", url.getUrlsIn()),
    //                     Updates.set("urlsOut", url.getUrlsOut()),
    //                     Updates.set("baseUrl", url.getBaseUrl()),
    //                     Updates.set("parents", url.getParents()),
    //                     Updates.set("childern", url.getChildren()),
    //                     Updates.set("isIndexed", url.isIndexed()),
    //                     Updates.set("lastTime", url.getLastTime()),
    //                     Updates.set("rank", url.getRank()),
    //                     Updates.set("a", url.getChildTXt()),
    //                     Updates.set("h1", url.getH1()),
    //                     Updates.set("h2", url.getH2()),
    //                     Updates.set("etag",url.getEtag()),
    //                     Updates.set("lastModified",url.getLastModified())
    //             );
    //             com.mongodb.client.result.UpdateResult result = docCollection.updateOne(filter, update);
    //             if (result.getMatchedCount() > 0) {
    //                 updatedCount++;
    //             }
    //         }
    //         logger.info("Successfully updated {} documents in MongoDB.", updatedCount);
    //     } catch (MongoException e) {
    //         logger.error("Failed to update batch: {}", e.getMessage());
    //         throw new RuntimeException("Failed to update batch in MongoDB", e);
    //     }
    // }

    public void updateBatch(List<Url> urls) {
        try {
            List<WriteModel<Document>> updates = new ArrayList<>();
            for (Url url : urls) {
                Bson filter = Filters.eq("url", url.getUrl());
                Bson update = Updates.combine(
                        Updates.set("title", url.getTitle()),
                        Updates.set("content", url.getContent()),
                        Updates.set("hashingDoc", url.getHashingDoc()),
                        // Updates.set("urlsIn", url.getParents() != null ? url.getParents().size() : 0),
                        // Updates.set("urlsOut", url.getChildren() != null ? url.getChildren().size() : 0),
                        Updates.set("baseUrl", url.getBaseUrl()),
                        Updates.set("parents", url.getParents() != null ? url.getParents() : new ArrayList<>()),
                        Updates.set("children", url.getChildren() != null ? url.getChildren() : new ArrayList<>()),
                        Updates.set("isIndexed", url.isIndexed()),
                        Updates.set("lastTime", url.getLastTime()),
                        Updates.set("rank", url.getRank()),
                        Updates.set("a", url.getChildTxt()),
                        Updates.set("h1", url.getH1()),
                        Updates.set("h2", url.getH2()),
                        Updates.set("etag", url.getEtag()),
                        Updates.set("lastModified", url.getLastModified())
                );
                updates.add(new UpdateOneModel<>(filter, update, new UpdateOptions().upsert(true)));
            }
            BulkWriteResult result = docCollection.bulkWrite(updates);
            logger.info("Successfully updated {} documents in MongoDB.", result.getModifiedCount());
        } catch (MongoException e) {
            logger.error("Failed to update batch: {}", e.getMessage());
            throw new RuntimeException("Failed to update batch in MongoDB", e);
        }
    }

    public List<Url> getUrlsBatch(int start, int end) {
        List<Url> urls = new ArrayList<>();
        FindIterable<Document> documents = docCollection.find()
                .skip(start)
                .limit(end - start);
        for (Document doc : documents) {
            urls.add(documentToUrl(doc));
        }
        return urls;
    }

    public List<String> findParents(String url) {
        Document query = new Document("children", url);
        return docCollection.find(query).into(new ArrayList<>()).stream()
                .map(doc -> doc.getString("url"))
                .collect(Collectors.toList());
    }

    public void addParentToUrl(String url, String parentUrl) {
        System.out.println("in adddddd");
        Document query = new Document("url", url);
        Document update = new Document("$addToSet", new Document("parents", parentUrl));
        docCollection.updateOne(query, update);
    }

    public List<Url> getAllUrlsSortedByRank() {
        List<Url> urls = new ArrayList<>();
        docCollection.find()
                .sort(new Document("rank", -1)) // Sort by rank in descending order
                .forEach(doc -> {
                    Url url = new Url(doc.getString("url"));
                    url.setTitle(doc.getString("title"));
                    url.setContent(doc.getString("content"));
                    url.setHashingDoc(doc.getString("hashingDoc"));
                    // url.setUrlsIn(doc.getInteger("urlsIn", 0));
                    // url.setUrlsOut(doc.getInteger("urlsOut", 0));
                    url.setBaseUrl(doc.getString("baseUrl"));
                    url.setParents(doc.get("parents", List.class));
                    url.setChildren(doc.get("children", List.class));
                    url.setIndexed(doc.getBoolean("isIndexed", false));
                    url.setLastTime(doc.getLong("lastTime"));
                    url.setRank(doc.getDouble("rank"));
                    url.setH1(doc.getString("h1"));
                    url.setH2(doc.getString("h2"));
                    url.setChildTxt(doc.getString("a"));
                    url.setEtag(doc.getString("etag"));
                    url.setLastModified(doc.getString("lastModified"));
                    urls.add(url);
                });
        return urls;
    }

    public Url getUrlByUrl(String url) {
        Document query = new Document("url", url);
        Document doc = docCollection.find(query).first();
        return doc != null ? documentToUrl(doc) : null;
    }

    private Document urlToDocument(Url url) {
        Document doc = new Document("url", url.getUrl())
                .append("title", url.getTitle())
                .append("content", url.getContent())
                .append("hashingDoc", url.getHashingDoc())
                // .append("urlsIn", url.getParents() != null ? url.getParents().size() : 0)
                // .append("urlsOut", url.getChildren() != null ? url.getChildren().size() : 0)
                .append("baseUrl", url.getBaseUrl())
                .append("parents", url.getParents())
                .append("children", url.getChildren())
                .append("isIndexed", url.isIndexed())
                .append("lastTime", url.getLastTime())
                .append("rank", url.getRank())
                .append("a", url.getChildTxt())
                .append("h1", url.getH1())
                .append("h2", url.getH2())
                .append("etag", url.getEtag())
                .append("lastModified", url.getLastModified());
        return doc;
    }

    private Url documentToUrl(Document doc) {
        Url url = new Url(doc.getString("url"));
        url.setTitle(doc.getString("title"));
        url.setContent(doc.getString("content"));
        url.setHashingDoc(doc.getString("hashingDoc"));
        url.setBaseUrl(doc.getString("baseUrl"));
        url.setParents(doc.get("parents", List.class));
        url.setChildren(doc.get("children", List.class));
        url.setIndexed(doc.getBoolean("isIndexed", false));
        url.setLastTime(doc.getLong("lastTime"));
        url.setRank(doc.getDouble("rank"));
        url.setH1(doc.getString("h1"));
        url.setH2(doc.getString("h2"));
        url.setChildTxt(doc.getString("a"));
        url.setEtag(doc.getString("etag"));
        url.setLastModified(doc.getString("lastModified"));
        return url;
    }
    

    public void updateUrlIfChanged(Url url, String newHash, Document updatedFields, List<String> newChildren) {
        Document query = new Document("url", url.getUrl());
        Document existingDoc = docCollection.find(query).first();
        if (existingDoc == null) {
            Document doc = urlToDocument(url);
            doc.putAll(updatedFields);
            doc.put("hashingDoc", newHash);
            doc.put("children", newChildren);
            docCollection.insertOne(doc);
            return;
        }

        String existingHash = existingDoc.getString("hashingDoc");
        if (existingHash != null && existingHash.equals(newHash)) {
            docCollection.updateOne(
                    query,
                    new Document("$set", new Document("lastTime", System.currentTimeMillis()))
            );
        } else {
            Document updateDoc = new Document("hashingDoc", newHash)
                    .append("lastTime", System.currentTimeMillis())
                    .append("children", newChildren)
                    .append("urlsOut", newChildren != null ? newChildren.size() : 0)
                    .append("linkStructureChanged", !newChildren.equals(existingDoc.get("children", List.class)));
            updateDoc.putAll(updatedFields);
            docCollection.updateOne(
                    query,
                    new Document("$set", updateDoc)
            );
        }
    }

    public void calculatePageRank() throws MongoException {
        long totalUrls = docCollection.countDocuments();
        if (totalUrls == 0) {
            logger.info("No URLs in database for PageRank calculation");
            return;
        }
        final double DAMPING_FACTOR = 0.85;
        double baseScore = 1.0 - DAMPING_FACTOR;
    
        // Step 1: Initialize ranks if not set
        docCollection.updateMany(
            new Document("rank", new Document("$exists", false)),
            new Document("$set", new Document("rank", 1.0 / totalUrls))
        );
    
        // Iterate 10 times
        for (int iteration = 0; iteration < 10; iteration++) {
            logger.info("PageRank iteration {}/10", iteration + 1);
    
            // Reset newRank field for this iteration to 0
            docCollection.updateMany(
                new Document(),
                new Document("$set", new Document("newRank", 0.0))
            );
    
            // Step 2: Compute contributions from all URLs
            int start = 0;
            while (start < totalUrls) {
                int end = (int) Math.min(start + BATCH_SIZE, totalUrls);
                logger.debug("Processing batch: URLs {} to {}", start, end);
    
                // Fetch the current batch of URLs with their ranks and children
                List<Document> batch = docCollection.find()
                    .projection(new Document("url", 1).append("rank", 1).append("children", 1))
                    .skip(start)
                    .limit(BATCH_SIZE)
                    .into(new ArrayList<>());
    
                if (batch.isEmpty()) {
                    break;
                }
    
                List<WriteModel<Document>> updates = new ArrayList<>();
                for (Document doc : batch) {
                    String url = doc.getString("url");
                    Double currentRank = doc.getDouble("rank");
                    if (currentRank == null) {
                        logger.warn("Rank for URL {} is null, setting to default initial value", url);
                        currentRank = 1.0 / totalUrls;
                    }
    
                    List<String> children = doc.get("children", List.class);
                    int outDegree = (children != null && !children.isEmpty()) ? children.size() : 1; // Avoid division by zero
    
                    double contribution = currentRank / outDegree;
                    if (children != null && !children.isEmpty()) {
                        for (String childUrl : children) {
                            updates.add(new UpdateOneModel<>(
                                new Document("url", childUrl),
                                new Document("$inc", new Document("newRank", DAMPING_FACTOR * contribution)),
                                new UpdateOptions().upsert(false)
                            ));
                        }
                    }
                }
    
                if (!updates.isEmpty()) {
                    docCollection.bulkWrite(updates);
                    logger.debug("Updated newRanks for {} URLs in batch", updates.size());
                }
    
                start += BATCH_SIZE;
            }
    
            // Step 3: Apply the PageRank formula for each URL
            start = 0;
            while (start < totalUrls) {
                int end = (int) Math.min(start + BATCH_SIZE, totalUrls);
                logger.debug("Applying PR formula for batch: URLs {} to {}", start, end);
    
                List<Document> batch = docCollection.find()
                    .projection(new Document("url", 1).append("newRank", 1))
                    .skip(start)
                    .limit(BATCH_SIZE)
                    .into(new ArrayList<>());
    
                if (batch.isEmpty()) {
                    break;
                }
    
                List<WriteModel<Document>> updates = new ArrayList<>();
                for (Document doc : batch) {
                    String url = doc.getString("url");
                    Double newRankSum = doc.getDouble("newRank");
                    if (newRankSum == null) {
                        newRankSum = 0.0; // No incoming links, use base score
                    }
                    double newRank = baseScore + newRankSum;
                    updates.add(new UpdateOneModel<>(
                        new Document("url", url),
                        new Document("$set", new Document("rank", newRank)),
                        new UpdateOptions().upsert(false)
                    ));
                }
    
                if (!updates.isEmpty()) {
                    docCollection.bulkWrite(updates);
                    logger.debug("Applied PR formula for {} URLs in batch", updates.size());
                }
    
                start += BATCH_SIZE;
            }
        }
    
        // Clean up temporary newRank field
        docCollection.updateMany(
            new Document(),
            new Document("$unset", new Document("newRank", ""))
        );
    
        logger.info("PageRank calculation completed for {} URLs", totalUrls);
    }

    // Optional: Close the connection
    /**
     * Safely closes the MongoDB connection
     */
    public void close() {
        if (isShuttingDown) {
            logger.warn("Already shutting down");
            return;
        }

        logger.info("Beginning MongoDB connection shutdown");
        isShuttingDown = true;

        if (mongoClient != null) {
            try {
                // Create a separate thread to handle the shutdown
                // This avoids the ClassNotFoundException issue during Maven shutdown
                Thread shutdownThread = new Thread(() -> {
                    try {
                        logger.info("Closing MongoDB client");
                        mongoClient.close();
                        logger.info("MongoDB connection closed successfully");
                    } catch (Exception e) {
                        logger.error("Error during MongoDB connection closure: {}", e.getMessage());
                    }
                });

                // Set as daemon to prevent it from keeping the application alive
                shutdownThread.setDaemon(true);
                shutdownThread.start();

                // Give the thread a short time to complete
                shutdownThread.join(2000);

            } catch (InterruptedException e) {
                logger.warn("Shutdown process was interrupted");
                Thread.currentThread().interrupt(); // Restore the interrupt status
            } catch (Exception e) {
                logger.error("Error during MongoDB connection closure: {}", e.getMessage());
            } finally {
                // Set to null to allow garbage collection
                mongoClient = null;
            }
        }
    }

    public static void main(String[] args) {
        // Initialize DBManager
        DBManager dbManager = new DBManager();
        // String content = dbManager.getDocContentById("https://chatgpt.com");
        // System.out.println("connntrnt" + content);
        dbManager.resetIndexedStatus();
        dbManager.close();
    }
}