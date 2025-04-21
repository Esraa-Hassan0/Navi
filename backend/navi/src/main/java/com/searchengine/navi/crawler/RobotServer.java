package com.searchengine.navi.crawler;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Scanner;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;


public class RobotServer {
    private static final String USER_AGENT = "NaviBot";
    private final Map<String, RobotsTxtCacheEntry> robotsCache;              // each robots.txt and its rules
    private static final long CACHE_TTL = 24 * 60 * 60 * 1000;               // the time to live for the cache (24 hours)

    public RobotServer() {
        this.robotsCache = new java.util.HashMap<>();
    }

    private static class RobotsTxtCacheEntry {                // hold the robots.txt rules and timestamp
        SimpleRobotRules rules;
        long timestamp;

        RobotsTxtCacheEntry(SimpleRobotRules rules, long timestamp) {
            this.rules = rules;
            this.timestamp = timestamp;
        }
    }

    public String getRobot(Url url) {
        
        return url.getBaseUrl() + "/robots.txt";

    }
    // public boolean isAllowedUrl(Url url) {
    //     String urlStr = url.getUrl();
    //     String robotsTxtUrl = getRobot(url);
    //     if (robotsTxtUrl == null) {
    //         return true; // Allow if URL is invalid
    //     }

        
    //     try {
    //         String hostPort = robotsTxtUrl.substring(0, robotsTxtUrl.length() - "/robots.txt".length());

    //         // Check cache
    //         RobotsTxtCacheEntry cacheEntry = robotsCache.get(hostPort);
    //         if (cacheEntry != null && (System.currentTimeMillis() - cacheEntry.timestamp) < CACHE_TTL) {
    //             return cacheEntry.rules.isAllowed(urlStr);
    //         }

    //         // Fetch robots.txt
    //         HttpURLConnection connection = (HttpURLConnection) new URL(robotsTxtUrl).openConnection();
    //         connection.setRequestProperty("User-Agent", USER_AGENT);
    //         connection.setConnectTimeout(5000);
    //         connection.setReadTimeout(5000);
    //         int statusCode;
    //         try {
    //             statusCode = connection.getResponseCode();
    //         } catch (IOException e) {
    //             connection.disconnect();
    //             throw e;
    //         }

    //         if (statusCode == 403 || statusCode == 429) {
    //             // Forbidden or too many requests: disallow all
    //             SimpleRobotRules rules = new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_NONE);
    //             robotsCache.put(hostPort, new RobotsTxtCacheEntry(rules, System.currentTimeMillis()));
    //             connection.disconnect();
    //             return false;
    //         }
    //         if (statusCode >= 400) {
    //             // Other errors (e.g., 404): allow all
    //             SimpleRobotRules rules = new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
    //             robotsCache.put(hostPort, new RobotsTxtCacheEntry(rules, System.currentTimeMillis()));
    //             connection.disconnect();
    //             return true;
    //         }

    //         // Read robots.txt content
    //         String robotsTxtContent;
    //         try (Scanner scanner = new Scanner(connection.getInputStream(), "UTF-8")) {
    //             robotsTxtContent = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
    //         } finally {
    //             connection.disconnect();
    //         }

    //         // Parse with crawler-commons
    //         SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
    //         SimpleRobotRules rules = parser.parseContent(robotsTxtUrl, robotsTxtContent.getBytes("UTF-8"),
    //                 "text/plain", USER_AGENT);

    //         // Cache rules
    //         robotsCache.put(hostPort, new RobotsTxtCacheEntry(rules, System.currentTimeMillis()));

    //         return rules.isAllowed(urlStr);
    //     } catch (IOException e) {
    //         System.err.println("Error fetching robots.txt for " + urlStr + ": " + e.getMessage());
    //         // Cache allow-all on error
    //         robotsCache.put(robotsTxtUrl.substring(0, robotsTxtUrl.length() - "/robots.txt".length()),
    //                 new RobotsTxtCacheEntry(new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL),
    //                         System.currentTimeMillis()));
    //         return true; // Allow by default
    //     }
    //}

}
