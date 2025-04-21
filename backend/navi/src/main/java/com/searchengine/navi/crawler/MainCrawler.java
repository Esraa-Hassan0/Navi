package com.searchengine.navi.crawler;

import java.io.FileNotFoundException;
import java.util.Queue;

public class MainCrawler {
    
    public static void main(String[] args) throws FileNotFoundException {
        WebCrawler webCrawler = new WebCrawler();
        // Queue<Url> q = webCrawler.getSeedList();
        // for (Url url : q) {
        //     System.out.println(url.getUrl());
        // }
        webCrawler.buildGraph();
    }

}
