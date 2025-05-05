package com.searchengine.navi.crawler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.net.URLEncoder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class RobotServer {

    // host -> (user-agent -> rules)
    private final Map<String, Map<String, List<String>>> rulesCache = new ConcurrentHashMap<>();

    private final String userAgent;


    public RobotServer() {
        this.userAgent =  "*";
    }

    /**
     * @return True if the URL is allowed, false otherwise.
     */
    public boolean isUrlAllowed(String url) {
        String encodedUrl = encodeUrl(url);
        URI uri;
        try {
            uri = URI.create(encodedUrl);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid URL: " + url + " - " + e.getMessage());
            return false; // Disallow invalid URLs
        }

        String host = uri.getHost();
        if (host == null) {
            System.err.println("No host found in URL: " + url);
            return false;
        }
        if (!rulesCache.containsKey(host)) {    // if the host is not in the cache fetch the robots.txt
            fetchAndCacheRobots(uri, host);
        }

        // Check the URL against the rules
        Map<String, List<String>> hostRules = rulesCache.get(host);
        if (hostRules == null) {               // if no rules are found allow as a safe default
            return true;
        }

        return isUrlAllowedInternal(url, hostRules);
    }

    private void fetchAndCacheRobots(URI baseUri, String host) {
        String robotsTxt = fetchRobotsTxt(baseUri);
        if (robotsTxt == null) {
            return;
        }

        Map<String, List<String>> rules = parseRobotsTxt(robotsTxt);
        rulesCache.put(host, rules);
    }

    private String fetchRobotsTxt(URI baseUri) {
        String robotsUrl = baseUri.getScheme() + "://" + baseUri.getHost() + "/robots.txt";
        try {
            URL url = new URL(robotsUrl);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                return content.toString();
            }
        } catch (IOException e) {
            System.err.println("Failed to fetch robots.txt for " + robotsUrl + ": " + e.getMessage());
            return null;
        }
    }

    private Map<String, List<String>> parseRobotsTxt(String robotsTxt) {
        Map<String, List<String>> rules = new ConcurrentHashMap<>();
        String[] lines = robotsTxt.split("\n");
        String currentUA = null;

        for (String line : lines) {
            line = line.trim().toLowerCase();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;                      // Skip empty lines and comments
            }

            if (line.startsWith("user-agent:")) {
                currentUA = extractValue(line);
                rules.computeIfAbsent(currentUA, k -> new ArrayList<>());
            } else if (line.startsWith("allow:") || line.startsWith("disallow:")) {
                if (currentUA != null) {
                    String path = extractValue(line);
                    if (!path.isEmpty()) {
                        rules.get(currentUA).add(line);
                    }
                }
            }
        }

        return rules;
    }

    private String extractValue(String line) {
        String[] parts = line.split(":", 2);
        return parts.length > 1 ? parts[1].trim() : "";
    }

    /**
     * Internal method to check if a URL is allowed based on the robots.txt rules.
     *
     * @param url       The URL to check.
     * @param hostRules The cached rules for the host.
     * @return True if the URL is allowed, false otherwise.
     */
    private boolean isUrlAllowedInternal(String url, Map<String, List<String>> hostRules) {
        // Get rules for the user-agent and default (*)
        // List<String> uaRules = hostRules.get(userAgent);
        List<String> defaultRules = hostRules.get("*");


        if (defaultRules == null) {           // if no rules are found allow as a safe default
            return true;
        }

        String path;
        try {
            URI uri = null;
            try {
                uri = new URI(encodeUrl(url));
            } catch (URISyntaxException e) {
                System.out.println("Failed to fetch the url in robots");
                return false;
            }
            path = uri.getPath();
            // Preserve trailing slash if it exists in the original URL
            if (url.endsWith("/") && !path.endsWith("/")) {
                path += "/";
            }
            if (path == null || path.isEmpty()) {
                path = "/";
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid URL: " + url + " - " + e.getMessage());
            return false; // Disallow invalid URLs
        }


        boolean allowed = true; // Default to allow if no rules match
        if (defaultRules != null) {
            allowed = applyRules(path, defaultRules, allowed);
        }

        return allowed;
    }

    private boolean applyRules(String path, List<String> rules, boolean defaultAllow) {
        if (rules == null || rules.isEmpty()) {
            return defaultAllow;   // No rules, allow by default
        }

        // Sort rules by path length (descending) to prioritize specific rules
        List<Rule> sortedRules = new ArrayList<>();
        for (String rule : rules) {
            String[] parts = rule.split(":", 2);
            String directive = parts[0].trim();
            String pattern = parts[1].trim();
            if (!pattern.isEmpty()) {
                sortedRules.add(new Rule(directive, pattern));
            }
        }
        sortedRules.sort((r1, r2) -> Integer.compare(r2.pattern.length(), r1.pattern.length()));

        // Apply rules in order of specificity
        for (Rule rule : sortedRules) {
            Pattern pattern = Pattern.compile(rule.regexPattern);
            Matcher matcher = pattern.matcher(path);
            if (matcher.find()) {
                return rule.directive.equals("allow");   // if the directive is allow return true else false
            }
        }

        return defaultAllow;
    }

    private String encodeUrl(String url) {
        try {
            return URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                    .replace("%2F", "/")
                    .replace("%3A", ":")
                    .replace("%3F", "?")
                    .replace("%3D", "=")
                    .replace("%26", "&");
        } catch (Exception e) {
            System.err.println("Failed to encode URL: " + url + " - " + e.getMessage());
            return url; // Return original URL as fallback
        }
    }

    // to store rules in regex pattern
    private static class Rule {
        String directive;
        String pattern;
        String regexPattern;

        Rule(String directive, String pattern) {
            this.directive = directive;
            this.pattern = pattern;
            this.regexPattern = pattern
                    .replace("*", ".*")           // Convert robots.txt wildcards to equivalent regex
                    .replace("?", "[?]")          // replace one character
                    .replace("$", "\\$");          // Escape end-of-string marker
            if (!this.regexPattern.endsWith(".*") && !this.regexPattern.endsWith("\\$")) {
                this.regexPattern += ".*";
            }
            this.regexPattern = Pattern.quote(this.regexPattern).replace("\\*", ".*").replace("\\.", ".");   // Escape regex special characters
        }
    }

    // Example usage
    // public static void main(String[] args) {
    //     RobotServer manager = new RobotServer();
    //     String url1 = "https://github.com/search/advanced";
    //     String url2 = "https://uk.wikipedia.org/wiki/SomeOtherPage";

    //     System.out.println("URL 1 allowed: " + manager.isUrlAllowed(url1));
    //     System.out.println("URL 2 allowed: " + manager.isUrlAllowed(url2));
    // }
}