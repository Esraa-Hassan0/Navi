package com.searchengine.navi.crawler;

import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;


public class UrlNomalizer {

    public void UrlNomalizer() {
        // TODO Auto-generated method stub
    }

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

        url = url.toLowerCase();
        String fixedUrl = url.replace("{", "%7B").replace("}", "%7D");  // to avoid error in URI becauce of { and }

        URI uri = new URI(fixedUrl);          // can throw URISyntaxException      //////////
        uri.normalize(); // Normalize the URI
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
        if (host != null && host.startsWith("www")) {
            host = host.substring(4);
            uri = new URI(uri.getScheme(), uri.getUserInfo(), host, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        }
        if (host != null && host.endsWith(".eg")) {
            host = host.substring(0, host.length() - 3);
            uri = new URI(uri.getScheme(), uri.getUserInfo(), host, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        }
        // Looking at http and https

        ///escape special characters in URLs that could break your request.
        // if (uri.getQuery() != null) {
        //     String query = uri.getQuery();
        //     String newQuery = query;
        //     try {
        //         newQuery = URLEncoder.encode(query, "UTF-8");
        //     } catch (UnsupportedEncodingException e) {
        //         System.out.println("Error in encoding query: " + query);
        //         e.printStackTrace();
        //     }
        //     uri = new URI(uri.getScheme(), uri.getUserInfo(), host, uri.getPort(), uri.getPath(), newQuery, uri.getFragment());
        // }
        // if (uri.getQuery() != null) {
        //     uri = new URI(uri.getScheme(), uri.getUserInfo(), host, uri.getPort(), uri.getPath(), null, uri.getFragment());
        // }
        // if (uri.getFragment() != null) {
        //     uri = new URI(uri.getScheme(), uri.getUserInfo(), host, uri.getPort(), uri.getPath(), uri.getQuery(), null);
        // }
        return ("https://" + uri.getHost() + uri.getPath()).toLowerCase();
        
    }


    // after normalization
    public void addBaseUrl (Url url) {
        try {
            URI uri = new URI(url.getUrl());
            String baseUrl = uri.getScheme() + "://" + uri.getHost();
            if (uri.getPort() != -1) {              // default port is already removed in normalization but if the port exist it is prefered to add
                baseUrl += ":" + uri.getPort();
            }
            url.setBaseUrl(baseUrl);
        } catch (URISyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
}
