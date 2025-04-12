package com.searchengine.dbmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.HashMap;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;
import com.searchengine.navi.indexer.Indexer.Token;
import com.searchengine.navi.indexer.Posting;

public class DBManager {
    private MongoClient mongoClient;
    private MongoDatabase DB;
    // I changed it to invertedIndex instead of invertedIndexer
    private MongoCollection<Document> invertedIndexCollection; // Fixed typo
    private MongoCollection<Document> docCollection;

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
            Document doc = invertedIndexerCollection.find(filter).first();

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
                    Aggregates.match(Filters.eq("postings.docId", docId)),
                    Aggregates.unwind("$postings"),
                    Aggregates.match(Filters.eq("postings.docId", docId)),
                    Aggregates.unwind("$postings.positions"),
                    Aggregates.match(Filters.eq("postings.positions.type", field)));

            // Get the AggregateIterable
            AggregateIterable<Document> aggregateIterable = invertedIndexerCollection.aggregate(pipeline);

            // Count the results by iterating
            int length = 0;
            for (Document doc : aggregateIterable) {
                length++;
            }

            return length;
        } catch (MongoException e) {
            System.err.println("Error retrieving document ID: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public double getAvgFieldLength(String field) {
        List<Bson> pipeline = Arrays.asList(
                Aggregates.unwind("$postings"),
                Aggregates.unwind("$postings.positions"),
                Aggregates.match(Filters.eq("postings.positions.type", field)),
                Aggregates.count("totalCount"));

        Document result = invertedIndexerCollection.aggregate(pipeline).first();
        double count = result != null ? result.getInteger("totalCount") : 0L;

        return count / getDocumentsCount();
    }

    // Retrieve all URLS in our db

    public ArrayList<Document> retriveURLs() {
        ArrayList<Document> urls = docCollection.find().projection(new Document("url", 1).append("_id", 0))
                .into(new ArrayList<>());

        return urls;
    }

    // Insert Inverted Index to the db

    // For now it is limited to 10 docs till we finalize our structure

    public void insertIntoInvertedIndex(HashMap<String, Token> invertedIndex)

    {
        int cnt = 0;

        for (String word : invertedIndex.keySet()) {
            Token token = invertedIndex.get(word);
            ArrayList<Document> postingsList = new ArrayList<>();

            // Create a posting document for each Token
            for (Posting posting : token.getPostings()) {
                Document postingDoc = new Document()
                        .append("docID", posting.getDocID())
                        .append("TF", posting.getTF());

                postingsList.add(postingDoc);
            }

            Document indexDoc = new Document().append("word", word).append("postingsList", postingsList);
            cnt++;
            if (cnt < 10)
                invertedIndexCollection.insertOne(indexDoc);
            else
                break;

        }
    }

    // Optional: Close the connection
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("MongoDB connection closed.");
        }
    }
}