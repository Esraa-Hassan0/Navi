package com.searchengine.navi.crawler;

import org.jsoup.Jsoup;
import java.io.*;
import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.jsoup.nodes.Document;
import org.jsoup.Connection;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;

public class WebCrawler implements Runnable { 

    private ArrayList<Url> visitedUrls;
    //private Set<Url> urls;
    private List<Page> pages = new ArrayList<Page>();
    private final int MAX_PAGES = 15;
    private final int MAX_DEPTH = 3;
    private UrlGraph graph = new UrlGraph();
    private UrlNomalizer normalizer = new UrlNomalizer();
    private static final String SEED_FILE_PATH = "files/seed.txt";
    HashMap<String, String> hashDoc = new HashMap<String, String>();
    Set<String> hashSet = new HashSet<String>();
    HashingManager hashingManager = new HashingManager(hashDoc, hashSet);

    public WebCrawler() {
        this.visitedUrls = new ArrayList<Url>();
        //this.urls = new HashSet<Url>();
    }
    
    @Override
    public void run() {
        // TODO Auto-generated method stub
    }

    
    private Document request(Url url) {
        try {
            Connection con = Jsoup.connect(url.getUrl()).userAgent("Mozilla/5.0 (Windows NT 10.0; ...) Chrome/90.0.4430.85 Safari/537.36").timeout(10000);
            Document doc = con.get();
            String contentType = con.response().contentType();              /// check if the content type is html
            if (contentType != null && contentType.contains("text/html")) {
                System.out.println("Content type: " + contentType);
            } else {
                System.out.println("Skipping non-HTML content type: " + contentType);
                return null;
            }
            if (con.response().statusCode() == 200) {
                visitedUrls.add(url);               ///////////////////////////////
                return doc;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void savePage (Page page, int i) {
        //sava each page to a different output file
        String fileName = "files/page" + i + ".txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write("url: " + page.getUrl().getUrl() + "\n");
            writer.write("title: " + page.getTitle() + "\n");
            writer.write("content: " + page.getContent() + "\n");
            writer.write("rank: " + page.getUrl().getRank() + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // BFS First Crawling
    public void buildGraph () throws FileNotFoundException {
        Queue<Url> seed = getSeedList();
        Set<String> visited = new HashSet<String>();
        List<Url> urls = new ArrayList<Url>();
        int cnt = 0;
        int i = 0;
        while (!seed.isEmpty() && visited.size() < MAX_PAGES) {
            Url url = seed.poll();
            try {
                url.setUrl(normalizer.normalizeUrl(url.getUrl()));
                normalizer.addBaseUrl(url);
            //     for (int i = 0; i < 5; i++) {
            //         String decoded = URLDecoder.decode(url.getUrl(), StandardCharsets.UTF_8);
            //         if (decoded.equals(url)) break;
            //         url.setUrl(decoded);
            // }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            if (visited.contains(url.getUrl())) continue;
            
            visited.add(url.getUrl());
            System.err.println("Visiting: " + url.getUrl());
            Document doc = request(url);
            if (doc != null) {
                String title = doc.title();
                String content = doc.body().text();
                String hashDoc = hashingManager.hashDoc(content); // need to add it ot hash set and check if it is already in the hash set
                //////////////////////////////////
                // we need to add rank after calculating it 
                /////////////////////////////////
                /// ////////////////////////////
                if (hashingManager.checkHashDoc(content)) {        //???????????????????????????????????????????????????????????
                    // this doc is already in the hash set
                    urls.add(url);
                    cnt++;
                    continue;
                } else {
                    hashingManager.addHashDoc(content);
                }
                // after checking the hash doc, we need to add the page to the graph
                int depth = 0;
                Page page = new Page(url);
                page.setTitle(title);
                page.setContent(content);
                page.setHashingDoc(hashDoc);
                page.setIndexed(false);
                pages.add(page);
                // add the url to the graph
                System.out.println("Content: " + content);
                System.out.println("Title: " + title);
                System.out.println("HashDoc: " + hashDoc);
                // savePage(page, i);
                // i++;
                for (Element  link : doc.select("a[href]")) {
                    String nextUrl = link.attr("abs:href");
                    if (!nextUrl.startsWith("http")) {
                        // skip if the URL is not absolute
                        System.out.println("Skipping non-absolute URL: " + nextUrl);
                        continue;
                    }
                    System.out.println("Next URL: " + nextUrl);
                    Url newUrl = new Url(nextUrl);
                    try {
                        newUrl.setUrl(normalizer.normalizeUrl(newUrl.getUrl()));
                        normalizer.addBaseUrl(newUrl);
                    } catch (URISyntaxException e) {
                        System.out.println("Error in normalizing URL: " + newUrl.getUrl());
                        e.printStackTrace();
                    }
                    try {
                        graph.addEdgeUrl(url, newUrl);
                    } catch (URISyntaxException e) {
                        System.out.println("Error in adding edge to graph: " + url.getUrl() + " -> " + newUrl.getUrl());
                        e.printStackTrace();
                    }
                    if (!visited.contains(newUrl.getUrl())) {
                        seed.add(newUrl);
                        depth++;
                        if (depth > MAX_DEPTH) {
                            break; // stop if we reach the maximum depth
                        }
                    }
                }
            }
        }

        // after building the graph ==> run page ranker
        graph.traverseToCalculateRank();
        System.out.println("///////////////////////////////////////////////////////////////////////////////////");
        for (Page page : pages) {
            //System.out.println("Page: " + page.getUrl().getUrl() + " Page Rank:" + page.getUrl().getRank() + " Title: " + page.getTitle() + " HashDoc: " + page.getHashingDoc());
            savePage(page, i);
            i++;
        }
        System.out.println("///////////////////////////////////////////////////////////////////////////////////");
        System.out.println("cnt " + cnt);
        System.out.println("Visited URLs: " + visited.size());
        for (Url url : urls) {
            System.out.println("duplicate URLs: " + url.getUrl());
        }
        System.out.println("///////////////////////////////////////////////////////////////////////////////////");
        for (String url : visited) {
            System.out.println("Visited URLs: " + url);
        }
    }

    public Queue<Url> getSeedList() throws FileNotFoundException {
        Queue<Url> seedList = new LinkedList<Url>();
        File file = new File(SEED_FILE_PATH);
        try (Scanner reader = new Scanner(file)) {
            while (reader.hasNextLine()) {
                String data = reader.nextLine();
                Url url = new Url(data);
                try {
                    url.setUrl(normalizer.normalizeUrl(data));
                    normalizer.addBaseUrl(url);
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
                seedList.add(url);
            }
            reader.close();
        }
        return seedList;
    }

}
