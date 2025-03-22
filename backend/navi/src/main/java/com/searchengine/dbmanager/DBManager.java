package com.searchengine.dbmanager;

import java.util.concurrent.TimeUnit;

import org.bson.Document;

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
    private MongoCollection<Document> invtedIndexerCollection;
    private MongoCollection<Document> docCollection;

    DBManager() {
        if (mongoClient == null) { // Ensure only one connection is created
            String connectionString = "mongodb+srv://esraa:navi123searchengine@cluster0.adp56.mongodb.net/";
            ServerApi serverApi = ServerApi.builder()
                    .version(ServerApiVersion.V1)
                    .build();

            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(connectionString))
                    .applyToSocketSettings(builder -> builder.connectTimeout(5, TimeUnit.SECONDS)) // 5 sec timeout
                    .build();

            try {
                mongoClient = MongoClients.create(settings); // Assign to the global variable
                DB = mongoClient.getDatabase("navi");
                invtedIndexerCollection = DB.getCollection("inverted index");
                docCollection = DB.getCollection("doc");

                System.out.println("✅ Successfully connected to MongoDB!");
            } catch (MongoException e) {
                e.printStackTrace();
            }
        }
    }
}
