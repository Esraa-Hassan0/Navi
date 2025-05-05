package com.searchengine.navi.crawler;

import org.jsoup.Jsoup;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mongodb.MongoException;
import org.jsoup.Connection;
import com.searchengine.dbmanager.DBManager;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.nodes.Document;

public class WebCrawler { 
    private static final Logger logger = LoggerFactory.getLogger(WebCrawler.class);
    private final int MAX_PAGES = 6000;
    private final int MAX_DEPTH = 5;
    private final int BATCH_SIZE = 1000;                // the number of pages to crawl in each batch
    private final String STATE_DIR = "files/state/";
    private final String VISITED_URLS = STATE_DIR + "visitedUrls.json";
    private final String URLS_TO_VISIT = STATE_DIR + "urlsToVisit.json";
    private final String PAGES_FILE = STATE_DIR + "pages.json";
    private final String HASHES_FILE = STATE_DIR + "hashingDocs.json";
    private static final String SEED_FILE_PATH = "files/seed.txt";
    private static final int PARTIAL_BATCH_FLUSH_INTERVAL = 1000;
    private final int MAX_PAGES_PER_DOMAIN = 10;       // the number of pages to crawl for each domain
    private static final double INITIAL_RANK = 1.0; // Initial PageRank for each URL

    //private UrlGraph graph;
    private UrlNomalizer normalizer;
    private RobotServer robotServer;
    private DBManager dbManager;
    private HashingManager hashingManager;
    private final List<Url> crawledUrls;
    private final PriorityBlockingQueue<Url> seed;              // Thread-safe queue for URLs to crawl
    private final Set<String> visited;                          // Thread-safe set of visited URLs
    private final List<Url> batchUrls;                          // For batch DB insertion
    private final ReentrantLock batchLock;                      // Synchronize batch operations
    private final AtomicInteger crawledPages;                   // Track crawled pages
    private final Gson gson;                                    // For JSON serialization
    private ExecutorService executor; 
    private final ConcurrentHashMap<String, Integer> domainPageCounts; // Track pages per domain
    
    public WebCrawler(int numThreads) {
        this.gson = new Gson();
        //this.graph = new UrlGraph();
        this.normalizer = new UrlNomalizer();
        this.robotServer = new RobotServer();
        this.dbManager = new DBManager();
        this.hashingManager = new HashingManager();
        this.crawledUrls = Collections.synchronizedList(new ArrayList<>());
        this.seed = new PriorityBlockingQueue<>(100, (u1, u2) -> Double.compare(u1.getRank(), u2.getRank())); // Priority queue for URLs to crawl
        this.visited = ConcurrentHashMap.newKeySet();      // thread safe version return a concurrent set
        this.batchUrls = Collections.synchronizedList(new ArrayList<>());
        this.batchLock = new ReentrantLock();
        this.crawledPages = new AtomicInteger(0);
        this.domainPageCounts = new ConcurrentHashMap<>();

        try {
            loadState();
            if(seed.isEmpty()) {
                seed.addAll(getSeedList());
                logger.info("Seed list loaded with {} URLs", seed.size());
            }
        } catch (URISyntaxException e) {
            logger.warn("Failed to load seed list", e);
            e.printStackTrace();
        }
    }
    

    private void loadState() throws URISyntaxException {
        logger.info("Loading state from files...");
        int loadedVisitedCount = 0, loadedSeedCount = 0, loadedCrawledCount = 0, loadedHashCount = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(VISITED_URLS))) {
            Set<String> loadVisited = gson.fromJson(br, new TypeToken<Set<String>>(){}.getType());   //expect a JSON array of strings and convert it into a Set<String>
            if (loadVisited != null) {
                visited.addAll(loadVisited);
                loadedVisitedCount = loadVisited.size();
            }
        } catch (IOException e) {
            logger.warn("Failed to load visited URLs: {}", e.getMessage());
        }
        try (BufferedReader br = new BufferedReader(new FileReader(URLS_TO_VISIT))) {
            List<Url> laodUrlsToVisit = gson.fromJson(br, new TypeToken<List<Url>>(){}.getType());
            if (laodUrlsToVisit != null) {
                for (Url url : laodUrlsToVisit) {
                    url.setUrl(normalizer.normalizeUrl(url.getUrl()));
                    normalizer.addBaseUrl(url);
                    seed.offer(url);
                }
                loadedSeedCount = laodUrlsToVisit.size();
            }
        } catch (IOException e) {
            logger.warn("Failed to load URLs to visit: {}", e.getMessage());
        }

        try (BufferedReader br = new BufferedReader(new FileReader(PAGES_FILE))) {
            List<Url> loadCrawledUrls = gson.fromJson(br, new TypeToken<List<Url>>(){}.getType());
            if (loadCrawledUrls != null) {
                crawledUrls.addAll(loadCrawledUrls);
                loadedCrawledCount = loadCrawledUrls.size();
            }
        } catch (IOException e) {
            logger.warn("Failed to load crawled URLs: {}", e.getMessage());
        }

 
        try (BufferedReader br = new BufferedReader(new FileReader(HASHES_FILE))) {
            Set<String> loadHashes = gson.fromJson(br, new TypeToken<Set<String>>(){}.getType());
            if (loadHashes != null) {
                hashingManager.getHashDoc().clear(); // Clear existing hashes
                hashingManager.getHashDoc().addAll(loadHashes);
                loadedHashCount = loadHashes.size();
            }
        } catch (IOException e) {
            logger.warn("Failed to load hashes: {}", e.getMessage());
        }

        logger.info("Loaded state: visited={}, seed={}, crawledUrls={}, hashes={}", 
                    loadedVisitedCount, loadedSeedCount, loadedCrawledCount, loadedHashCount);
    }

    private void saveState() {
        logger.info("Saving state to files.......");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(VISITED_URLS))) {
            gson.toJson(visited, bw);
            logger.debug("Saved visited URLs: count={}", visited.size());
        } catch (IOException e) {
            logger.error("Error saving visited URLs: {}", e.getMessage());
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(URLS_TO_VISIT))) {
            List<Url> seedList = new ArrayList<>();
            seed.drainTo(seedList);
            gson.toJson(seedList, bw);
            logger.debug("Saved URLs to visit: count={}", seedList.size());
            // Re-add to seed to preserve state
            seed.addAll(seedList);
        } catch (IOException e) {
            System.err.println("Error saving URLs to visit: " + e.getMessage());
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(PAGES_FILE))) {
            gson.toJson(crawledUrls, bw);
            logger.debug("Saved crawled URLs: count={}", crawledUrls.size());
        } catch (IOException e) {
            System.err.println("Error saving crawled URLs: " + e.getMessage());
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(HASHES_FILE))) {
            Set<String> hashes = hashingManager.getHashDoc();
            gson.toJson(hashes, bw);
            logger.debug("Saved hashes: count={}", hashes.size());
        } catch (IOException e) {
            logger.error("Error saving hashes: {}", e.getMessage());
        }

        logger.info("State saved: visited={}, seed={}, crawledUrls={}, hashes={}", 
                    visited.size(), seed.size(), crawledUrls.size(), hashingManager.getHashDoc().size());
        
    }

    
    private Document request(Url url, String etag, String lastModified) {
        try {
            Connection con = Jsoup.connect(url.getUrl())
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/90.0.4430.85")
            .timeout(15000)
            .ignoreHttpErrors(true)
            .followRedirects(true);

            if (etag != null) {
                con.header("If-None-Match", etag);
            }
            if (lastModified != null) {
                con.header("If-Modified-Since", lastModified);
            }

            Connection.Response response = con.execute();
            int statusCode = response.statusCode();
            if (statusCode == 304) {
                logger.info("URL {} not modified, skipping", url.getUrl());
                return null;
            }

            String contentType = response.contentType();
            if (contentType != null && contentType.contains("text/html")) {
                System.out.println("Content type: " + contentType);
                String contentLanguage = response.header("Content-Language");
                Document doc = response.parse();
                System.out.println("Content-Language: " + contentLanguage);
                String content = doc.body() != null ? doc.body().text() : "";
                if (contentLanguage != null && !contentLanguage.toLowerCase().contains("en")) {
                    logger.info("Skipping URL {} due to non-English Content-Language: {}", url.getUrl(), contentLanguage);
                    return null;
                }
                
                if (isLikelyNonEnglish(content)) {
                    logger.info("Skipping URL {} due to detected non-English content in checker", url.getUrl());
                    return null;
                }
                String newEtag = response.header("ETag");
                String newLastModified = response.header("Last-Modified");
                if (newEtag != null) {
                    url.setEtag(newEtag);
                }
                if (newLastModified != null) {
                    url.setLastModified(newLastModified);
                }
                return doc;
            } else {
                logger.info("Skipping URL {} due to unsupported content type: {}", url.getUrl(), contentType);
                return null;
            }
        } catch (IOException e) {
            System.err.println("Error fetching " + url.getUrl() + ": " + e.getMessage());
        }
        return null;
    }

    private boolean isLikelyNonEnglish(String content) {
        if (content == null || content.isEmpty()) return false;
        long nonAsciiCount = content.chars().filter(c -> c > 127).count();
        double nonAsciiRatio = (double) nonAsciiCount / content.length();
        return nonAsciiRatio > 0.1;  // if more than 10% of character are non-ASCII, assume non English
    }

    private String getDomain(String url) {
        try {
            URL u = new URL(url);
            return u.getHost();
        } catch (Exception e) {
            logger.warn("Failed to extract domain for URL {}: {}", url, e.getMessage());
            return "";
        }
    }

    public void startCrawling(int numThreads) {
        logger.info("Starting crawling with {} threads", numThreads);
        executor = Executors.newFixedThreadPool(numThreads);
    
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    crawl();
                } catch (Exception e) {
                    logger.error("Unexpected error in crawl thread: {}", e.getMessage(), e);
                }
            });
        }
    
        executor.shutdown();
        try {
            if (!executor.awaitTermination(9, TimeUnit.HOURS)) {
                logger.warn("Crawling timed out, forcing shutdown");
                executor.shutdownNow();
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.error("Executor did not terminate properly");
                }
            }
        } catch (InterruptedException e) {
            logger.warn("Crawling interrupted, forcing shutdown", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            // batchLock.lock();
            // try {
            //     if (!batchUrls.isEmpty()) {
            //         logger.info("Flushing final batch of {} URLs", batchUrls.size());
            //         List<Url> finalBatch = new ArrayList<>(batchUrls);
            //         dbManager.insertBatch(finalBatch);
            //         logger.info("Inserted {} URLs in final batch", finalBatch.size());
            //         logger.info("count of crawledPages {}", crawledPages.get());
            //         logger.info("count of crawledPages array {}", crawledUrls.size());
            //         batchUrls.clear();
            //     }
            // } catch (MongoException e) {
            //     logger.error("Failed to insert final batch: {}", e.getMessage());
            //     if (e.getCode() == 11000) { // Duplicate key error
            //         logger.warn("Duplicate URL detected, resolving...");
            //         // Handle duplicate by updating instead (implement update logic if needed)
            //     }
            // } finally {
            //     batchLock.unlock();
            // }
            flushBatch();
    
            saveState();
            try {
                logger.info("Calculating PageRank...");
                dbManager.calculatePageRank();
                logger.info("PageRank calculation completed.");
            } catch (MongoException e) {
                logger.error("Failed to calculate PageRank: {}", e.getMessage());
            }
    
            try {
                dbManager.close();
                logger.info("Database connection closed");
                // Give the MongoDB driver a little time to clean up its threads
                Thread.sleep(1000);
            } catch (Exception e) {
                logger.error("Failed to close database connection: {}", e.getMessage());
            }
    
            logger.info("Crawling completed. Total pages crawled: {}, duplicates skipped: {}", 
                        crawledPages.get(), hashingManager.getDuplicateCount());
        }
    }

    private void flushBatch() {
        batchLock.lock();
        try {
            if (!batchUrls.isEmpty()) {
                logger.info("Flushing batch of {} URLs", batchUrls.size());
                List<Url> batchToInsert = new ArrayList<>(batchUrls);
                try {
                    dbManager.insertBatch(batchToInsert);
                    logger.info("Inserted {} URLs in batch", batchToInsert.size());
                } catch (MongoException e) {
                    logger.error("Failed to insert batch: {}", e.getMessage());
                    if (e.getCode() == 11000) {
                        logger.warn("Duplicate URL detected, resolving...");
                    }
                }
                batchUrls.clear();
            }
        } finally {
            batchLock.unlock();
        }
        System.gc();
    }

    // BFS First Crawling
    private void crawl() {
        while (!seed.isEmpty()) {
            int currentCount = crawledPages.getAndIncrement();
            if (currentCount >= MAX_PAGES) {
                logger.info("Reached maximum page limit of {}. Stopping crawl.", MAX_PAGES);
                crawledPages.decrementAndGet(); // Decrement to reflect the actual number of crawled pages
                break;
            }
            Url url;
            try {
                url = seed.poll(1, TimeUnit.SECONDS);
                if (url == null) break;
            } catch (InterruptedException e) {
                logger.warn("Crawl thread interrupted", e);
                Thread.currentThread().interrupt();
                break;
            }

            if (url.getDepth() > MAX_DEPTH) {
                logger.debug("Skipping URL due to depth limit: {} (depth: {})", url.getUrl(), url.getDepth());
                continue;
            }
            
            try {
                processUrl(url, currentCount);
            } catch (URISyntaxException e) {
                logger.error("Failed to process URL {}: {}", url.getUrl(), e.getMessage());
                crawledPages.decrementAndGet();
                continue;
            }
            batchLock.lock();
            try {
                if (batchUrls.size() >= BATCH_SIZE) {
                    logger.info("Batch size reached ({} URLs), inserting at count {}", batchUrls.size(), currentCount + 1);
                    List<Url> batchToInsert = new ArrayList<>(batchUrls);
                    try {
                        dbManager.insertBatch(batchToInsert);
                        logger.info("Inserted {} URLs at count {}", batchToInsert.size(), currentCount + 1);
                        for (Url child : batchToInsert) {
                            List<String> children = child.getChildren();
                            if (children != null) {
                                for (String childUrl : children) {
                                    dbManager.addParentToUrl(childUrl, child.getUrl());
                                }
                            }
                        }
                    } catch (MongoException e) {
                        logger.error("Failed to insert batch at count {}: {}", currentCount + 1, e.getMessage());
                    }
                    batchUrls.clear();
                    saveState();
                    logger.info("Crawled {} pages, state saved", currentCount + 1);
                }
                // Flush partial batch periodically
                if ((currentCount + 1) % PARTIAL_BATCH_FLUSH_INTERVAL == 0 && !batchUrls.isEmpty()) {
                    logger.info("Periodic flush of partial batch ({} URLs) at count {}", batchUrls.size(), currentCount + 1);
                    List<Url> batchToInsert = new ArrayList<>(batchUrls);
                    try {
                        dbManager.insertBatch(batchToInsert);
                        logger.info("Inserted {} URLs in partial batch at count {}", batchToInsert.size(), currentCount + 1);
                        for (Url child : batchToInsert) {
                            List<String> children = child.getChildren();
                            if (children != null) {
                                for (String childUrl : children) {
                                    dbManager.addParentToUrl(childUrl, child.getUrl());
                                }
                            }
                        }
                    } catch (MongoException e) {
                        logger.error("Failed to insert partial batch at count {}: {}", currentCount + 1, e.getMessage());
                    }
                    batchUrls.clear();
                   saveState();
                    logger.info("Crawled {} pages, state saved", currentCount + 1);
                }
            } finally {
                batchLock.unlock();
            }
        }
        
    }
    
    private void processUrl(Url url, int currentCount) throws URISyntaxException {
        String normalizedUrl = normalizer.normalizeUrl(url.getUrl());
        if (normalizedUrl == null) {
            logger.debug("Skipping invalid URL: {}", url.getUrl());
            crawledPages.decrementAndGet();  // undo
            return;
        }
        url.setUrl(normalizedUrl);
        normalizer.addBaseUrl(url);

        String domain = getDomain(normalizedUrl);
        int domainCount = domainPageCounts.getOrDefault(domain, 0);
        if (domainCount >= MAX_PAGES_PER_DOMAIN) {
            logger.debug("Skipping URL {} due to domain page limit: {} (count: {})", url.getUrl(), domain, domainCount);
            crawledPages.decrementAndGet();
            return;
        }
        
        if (!visited.add(url.getUrl())){
            logger.debug("Skipping already visited URL: {}", url.getUrl());
            crawledPages.decrementAndGet();
            return;
        } 
       if(!robotServer.isUrlAllowed(url.getUrl())) {
            logger.debug("Skipping URL due to robots.txt: {}", url.getUrl());
            visited.remove(url.getUrl());
            crawledPages.decrementAndGet();
           return;
       }
        
        Document doc = request(url, url.getEtag(), url.getLastModified());
        if (doc == null) {
            visited.remove(url.getUrl());
            crawledPages.decrementAndGet();
            return;
        }
        String title = doc.title() != null ? doc.title() : "";
        String content = doc.body() != null ? doc.body().text() : "";
        String h1 = doc.select("h1").text();
        String h2 = doc.select("h2").text();
        String ChildTxt = doc.select("a[href]").text();
        String hashDoc = hashingManager.hashDoc(content);
        
        synchronized (hashingManager) {
            if (hashingManager.checkHashDoc(content)) {
                // this doc is already in the hash set
                logger.debug("Skipping duplicate content for URL: {}", url.getUrl());
                visited.remove(url.getUrl());
                crawledPages.decrementAndGet();
                return;
            } else {
                hashingManager.addHashDoc(content);
            }
        }
        
        url.setTitle(title);
        url.setContent(content);
        url.setHashingDoc(hashDoc);
        url.setIndexed(false);
        url.setBaseUrl(url.getBaseUrl());
        url.setH1(h1);
        url.setH2(h2);
        url.setChildTxt(ChildTxt);
        url.setLastTime(System.currentTimeMillis());

        
        ///////////// maybe need to add a depth for crawling
        
        List<String> children = new ArrayList<>();
        if (url.getDepth() < MAX_DEPTH) {
            for (Element  link : doc.select("a[href]")) {
                String nextUrl = link.attr("abs:href");
                if (!nextUrl.startsWith("http")) {
                    logger.debug("Skipping non-HTTP URL: {}", nextUrl);
                    continue;
                }
                String newNormalizedUrl = normalizer.normalizeUrl(nextUrl);
                if (newNormalizedUrl == null) continue;    // to avoid null pointer exception
                children.add(newNormalizedUrl);
                Url newUrl = new Url(newNormalizedUrl);
                normalizer.addBaseUrl(newUrl);
                newUrl.setDepth(url.getDepth() + 1); // Increment depth for child URLs
                newUrl.setRank(1.0);
                newUrl.addParent(url.getUrl());

                if (!visited.contains(newUrl.getUrl())) {
                    seed.offer(newUrl);
                }
            }
            url.setChildren(children);
        }

        batchLock.lock();
        try {
            batchUrls.add(url);
            domainPageCounts.compute(domain, (k, v) -> (v == null) ? 1 : v + 1);
            logger.debug("Domain {} reached limit of {}", domain, domainPageCounts.get(domain));
        } finally {
            batchLock.unlock();
        }
        
        synchronized (crawledUrls) {
            crawledUrls.add(url);
        }
        System.out.println("///////////////////////////////////////////////////////////////////////");
        saveFile(url.getUrl());
    }

    public void recrawl(int numThreads) {
        logger.info("Recrawling URLs...");
        List<Url> urlsToRecrawl = dbManager.getAllUrlsSortedByRank();
        logger.info("Recrawling {} URLs", urlsToRecrawl.size());
        seed.clear();
        seed.addAll(urlsToRecrawl);
        crawledPages.set(0);
        batchUrls.clear();
        domainPageCounts.clear();
        executor = Executors.newFixedThreadPool(numThreads);
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    recrawlThread();
                } catch (Exception e) {
                    logger.error("Unexpected error in recrawl thread: {}", e.getMessage(), e);
                }
            });
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                logger.warn("Recrawling timed out, forcing shutdown");
                executor.shutdownNow();
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.error("Executor did not terminate properly");
                }
            }
        } catch (InterruptedException e) {
            logger.warn("Recrawling interrupted, forcing shutdown", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            flushBatch();
            saveState();
            long changedCount = crawledUrls.stream().filter(Url::isLinkStructureChanged).count();
            if (changedCount > 0) {
                logger.info("Recalculating PageRank because changed count {}", changedCount);
                try {
                    dbManager.calculatePageRank();
                    logger.info("PageRank recalculation completed.");
                } catch (MongoException e) {
                    logger.error("Failed to calculate PageRank: {}", e.getMessage());
                }
            }
            logger.info("Recrawling completed. Total pages processed: {}, Changed URLs: {}, Total Crawled URLs: {}",
                    crawledPages.get(), changedCount, crawledUrls.size());
        }
    }

    private void recrawlThread() {
        while (!seed.isEmpty()) {
            int currentCount = crawledPages.getAndIncrement();
            if (currentCount >= MAX_PAGES) {
                logger.info("Reached maximum page limit of {}. Stopping recrawl.", MAX_PAGES);
                crawledPages.decrementAndGet();
                break;
            }
            Url url;
            try {
                url = seed.poll(1, TimeUnit.MILLISECONDS); // Reduced polling wait for speed
                if (url == null) break;
            } catch (InterruptedException e) {
                logger.warn("Recrawl thread interrupted", e);
                Thread.currentThread().interrupt();
                break;
            }
            try {
                recrawlUrl(url, currentCount);
            } catch (Exception e) {
                logger.error("Failed to recrawl URL {}: {}", url.getUrl(), e.getMessage());
                crawledPages.decrementAndGet();
                continue;
            }
            batchLock.lock();
            try {
                if (batchUrls.size() >= BATCH_SIZE) {
                    logger.info("Batch size reached ({} URLs), updating at count {}", batchUrls.size(), currentCount + 1);
                    List<Url> batchToUpdate = new ArrayList<>(batchUrls);
                    try {
                        dbManager.updateBatch(batchToUpdate);
                        logger.info("Updated {} URLs at count {}", batchToUpdate.size(), currentCount + 1);
                    } catch (MongoException e) {
                        logger.error("Failed to update batch at count {}: {}", currentCount + 1, e.getMessage());
                    }
                    batchUrls.clear();
                    synchronized (crawledUrls) {
                        crawledUrls.clear();
                    }
                    System.gc();
                   saveState();
                    logger.info("Processed {} pages, state saved", currentCount + 1);
                }
                if ((currentCount + 1) % PARTIAL_BATCH_FLUSH_INTERVAL == 0 && !batchUrls.isEmpty()) {
                    logger.info("Periodic flush of partial batch ({} URLs) at count {}", batchUrls.size(), currentCount + 1);
                    List<Url> batchToUpdate = new ArrayList<>(batchUrls);
                    try {
                        dbManager.updateBatch(batchToUpdate);
                        logger.info("Updated {} URLs in partial batch at count {}", batchToUpdate.size(), currentCount + 1);
                    } catch (MongoException e) {
                        logger.error("Failed to update partial batch at count {}: {}", currentCount + 1, e.getMessage());
                    }
                    batchUrls.clear();
                    synchronized (crawledUrls) {
                        crawledUrls.clear();
                    }
                    System.gc();
                    saveState();
                    logger.info("Processed {} pages, state saved", currentCount + 1);
                }
            } finally {
                batchLock.unlock();
            }
        }
    }

    private void recrawlUrl(Url url, int currentCount) throws URISyntaxException {
        String normalizedUrl = normalizer.normalizeUrl(url.getUrl());
        if (normalizedUrl == null) {
            logger.debug("Skipping invalid URL: {}", url.getUrl());
            crawledPages.decrementAndGet();
            return;
        }
        url.setUrl(normalizedUrl);
        normalizer.addBaseUrl(url);

        String domain = getDomain(normalizedUrl);
        int domainCount = domainPageCounts.getOrDefault(domain, 0);
        if (domainCount >= MAX_PAGES_PER_DOMAIN) {
            logger.debug("Skipping URL {} due to domain page limit: {} (count: {})", url.getUrl(), domain, domainCount);
            crawledPages.decrementAndGet();
            return;
        }

        if (!robotServer.isUrlAllowed(url.getUrl())) {
            logger.debug("Skipping URL due to robots.txt: {}", url.getUrl());
            crawledPages.decrementAndGet();
            return;
        }

        Url existingUrl = dbManager.getUrlByUrl(url.getUrl());
        String existingHash = (existingUrl != null) ? existingUrl.getHashingDoc() : null;

        Document doc = request(url, url.getEtag(), url.getLastModified());
        if (doc == null) {
            if (existingHash != null) {
                url.setHashingDoc(existingHash);
                url.setTitle(existingUrl.getTitle());
                url.setContent(existingUrl.getContent());
                url.setH1(existingUrl.getH1());
                url.setH2(existingUrl.getH2());
                url.setChildTxt(existingUrl.getChildTxt());
                url.setLastTime(System.currentTimeMillis());
                url.setRank(existingUrl.getRank());
                batchLock.lock();
                try {
                    batchUrls.add(url);
                } finally {
                    batchLock.unlock();
                }
            }
            crawledPages.decrementAndGet();
            return;
        }

        String title = doc.title() != null ? doc.title() : "";
        String content = doc.body() != null ? doc.body().text() : "";
        String h1 = doc.select("h1").text();
        String h2 = doc.select("h2").text();
        String childTxt = doc.select("a[href]").text();
        String newHash = hashingManager.hashDoc(content);

        // Compare hashes to check for content change
        if (existingHash != null && existingHash.equals(newHash)) {
            logger.debug("No content change detected for URL: {}", url.getUrl());
            url.setHashingDoc(existingHash);
            url.setTitle(existingUrl.getTitle());
            url.setContent(existingUrl.getContent());
            url.setH1(existingUrl.getH1());
            url.setH2(existingUrl.getH2());
            url.setChildTxt(existingUrl.getChildTxt());
            url.setLastTime(System.currentTimeMillis());
            url.setRank(existingUrl.getRank());
        } else {
            logger.info("Content changed detected for URL: {}", url.getUrl());
            url.setTitle(title);
            url.setContent(content);
            url.setHashingDoc(newHash);
            url.setH1(h1);
            url.setH2(h2);
            url.setChildTxt(childTxt);
            url.setLastTime(System.currentTimeMillis());
            url.setRank(existingUrl != null ? existingUrl.getRank() : INITIAL_RANK);

            List<String> newChildren = new ArrayList<>();
            if (url.getDepth() < MAX_DEPTH) {
                for (Element link : doc.select("a[href]")) {
                    String nextUrl = link.attr("abs:href");
                    if (!nextUrl.startsWith("http")) {
                        logger.debug("Skipping non-HTTP URL: {}", nextUrl);
                        continue;
                    }
                    String newNormalizedUrl = normalizer.normalizeUrl(nextUrl);
                    if (newNormalizedUrl == null) continue;    // to avoid null pointer exception
                    newChildren.add(newNormalizedUrl);
                }
            }
            boolean structureChanged = !url.getChildren().equals(newChildren);
            url.setLinkStructureChanged(structureChanged);
            url.setChildren(newChildren);
        }
        //dbManager.updateUrlIfChanged(url, newHash, updatedFields, newChildren);

        batchLock.lock();
        try {
            batchUrls.add(url);
            domainPageCounts.compute(domain, (k, v) -> (v == null) ? 1 : v + 1);
        } finally {
            batchLock.unlock();
        }
        synchronized (crawledUrls) {
            crawledUrls.add(url);
        }
        saveFile(url.getUrl());
        url.setContent(null);
    }
    
    private Collection<Url> getSeedList() throws URISyntaxException {
        List<Url> seedList = new ArrayList<>();
        try (Scanner reader = new Scanner(new File(SEED_FILE_PATH))) {
            while (reader.hasNextLine()) {
                String data = reader.nextLine().trim();
                if (!data.isEmpty()) {
                    Url url = new Url(data);
                    String normalized = normalizer.normalizeUrl(data);
                    if (normalized != null) {
                        url.setUrl(normalized);
                        normalizer.addBaseUrl(url);
                        seedList.add(url);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            logger.warn("Seed file not found: {}", SEED_FILE_PATH, e);
        }
        return seedList;
    }

    public void saveFile(String url) {
        try {
            String fileName = "visited.txt";
            File file = new File("files/" + fileName);
            if (!file.exists()) {
                file.createNewFile();
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
                writer.write(url);
                writer.newLine();
            }
        } catch (IOException e) {
            logger.error("Error saving URL to file: {}", e.getMessage());
        }
    }

}
