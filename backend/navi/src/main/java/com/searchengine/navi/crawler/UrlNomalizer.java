package com.searchengine.navi.crawler;

import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;


public class UrlNomalizer {

    private final ConcurrentHashMap<String, String> normalizedCache = new ConcurrentHashMap<>();


    /* 
    Steps for URL Normalization in Java
    * Convert to Lowercase  => done
    * Remove Default Ports  => done
    * Remove Trailing Slashes => done
    * Remove Fragment Identifiers => done
    * Decode URL-Encoded Characters
    * Canonicalize the URL (e.g., remove www)
    */
    /* 
    scheme://[user-info@]host[:port][/path][?query][#fragment]
    https://user:pass@example.com:8080/path/to/resource?query=param#section
    */
    public String normalizeUrl(String url) throws URISyntaxException {
        if (url == null || url.trim().isEmpty()) {
            System.out.println("URL is null");
            return null;
        }
        String cached = normalizedCache.get(url);
        if (cached != null) {
            return cached;
        }

        try {
            String fixedUrl = url.trim().toLowerCase();
            String encodedUrl = URLEncoder.encode(fixedUrl, StandardCharsets.UTF_8.toString())
                    .replace("%2F", "/")
                    .replace("%3A", ":")
                    .replace("%3F", "?")
                    .replace("%3D", "=")
                    .replace("%26", "&");
                        
            if (!encodedUrl.startsWith("http://") && !encodedUrl.startsWith("https://")) {
                encodedUrl = "https://" + encodedUrl; // Add https if not present
            }
            
            URI uri = new URI(encodedUrl).normalize();
            //uri = new URI("https", uri.getUserInfo(), uri.getHost(), -1, uri.getPath(), uri.getQuery(), uri.getFragment());
            int port = uri.getPort();
            // Remove Default Ports
            if (port == 80 || port == 443) {
                uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), -1, uri.getPath(), uri.getQuery(), uri.getFragment());
            } 
            // Remove Fragment Identifiers
            String fragment = uri.getFragment();
            if (fragment != null) {
                uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), null);
            }
            // Remove Trailing Slashes
            String path = uri.getPath();
            if (path != null && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
                uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), path, uri.getQuery(), uri.getFragment());
            }
            // Remove www
            String host = uri.getHost();
            if (host != null && host.startsWith("www2")) {
                host = host.substring(5);
                uri = new URI(uri.getScheme(), uri.getUserInfo(), host, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
            }
            if (host != null && host.startsWith("www")) {
                host = host.substring(4);
                uri = new URI(uri.getScheme(), uri.getUserInfo(), host, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
            }
            if (host != null && host.endsWith(".eg")) {
                host = host.substring(0, host.length() - 3);
                uri = new URI(uri.getScheme(), uri.getUserInfo(), host, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
            }
            // Decode URL-encoded characters
            String decodedPath = URLDecoder.decode(uri.getPath(), StandardCharsets.UTF_8.toString());
            String normalized = uri.getScheme() + "://" + uri.getHost() + decodedPath;
            normalized = normalized.toLowerCase();
            normalizedCache.put(url, normalized);
            return normalized;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            System.out.println("Invalid URL: " + url);
            return null;
        } catch (UnsupportedEncodingException e) {
            System.out.println("Encoding error for URL: " + url + " - " + e.getMessage());
            return null;
        }
    }
        


    // after normalization
    public void addBaseUrl (Url url) {
        if (url.getUrl() == null) return;
        try {
            // Pre-encode the URL to handle illegal characters
            String encodedUrl;
            encodedUrl = URLEncoder.encode(url.getUrl(), StandardCharsets.UTF_8.toString())
                    .replace("%2F", "/")
                    .replace("%3A", ":")
                    .replace("%3F", "?")
                    .replace("%3D", "=")
                    .replace("%26", "&");
            URI uri = new URI(encodedUrl);
            String baseUrl = uri.getScheme() + "://" + uri.getHost();
            int port = uri.getPort();
            if (port != -1 && port != 80 && port != 443) {              // default port is already removed in normalization but if the port exist it is prefered to add
                baseUrl += ":" + uri.getPort();
            }
        url.setBaseUrl(baseUrl);
        } catch (URISyntaxException e) {
            System.out.println("Faild to set base Url for " + url.getUrl() + ": " + e.getMessage());
            
        } catch (UnsupportedEncodingException e) {
            System.out.println("Faild to set base Url for " + url.getUrl() + ": " + e.getMessage());
        }

    }

}
