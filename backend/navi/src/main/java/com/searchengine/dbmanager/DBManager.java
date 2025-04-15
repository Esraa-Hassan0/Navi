package com.searchengine.dbmanager;

import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class DBManager {
    private MongoClient mongoClient;
    private MongoDatabase DB;
    private MongoCollection<Document> invertedIndexerCollection; // Fixed typo
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
                invertedIndexerCollection = DB.getCollection("inverted index"); // Fixed typo
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

    // Optional: Close the connection
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("MongoDB connection closed.");
        }
    }
}