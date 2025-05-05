package com.searchengine.navi.crawler;

import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.util.Queue;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.mongodb.MongoException;
import com.searchengine.dbmanager.DBManager;

public class MainCrawler {
    private static final Logger logger = LoggerFactory.getLogger(MainCrawler.class);
    public static void main(String[] args) throws FileNotFoundException {
                int numThreads = 20;
                WebCrawler crawler = new WebCrawler(numThreads);
                crawler.startCrawling(numThreads);
        //         DBManager dbManager = null;
        // try {
        //     logger.info("Initializing DBManager to run PageRank calculation...");
        //     dbManager = new DBManager();

        //     logger.info("Starting PageRank calculation on existing database data...");
        //     dbManager.calculatePageRank();
        //     logger.info("PageRank calculation completed successfully.");

        // } catch (MongoException e) {
        //     logger.error("Failed to calculate PageRank: {}", e.getMessage());
        // } catch (Exception e) {
        //     logger.error("Unexpected error during PageRank calculation: {}", e.getMessage());
        // } finally {
        //     if (dbManager != null) {
        //         try {
        //             dbManager.close();
        //             logger.info("Database connection closed.");
        //         } catch (Exception e) {
        //             logger.error("Failed to close database connection: {}", e.getMessage());
        //         }
        //     }
        // }
    }

}
