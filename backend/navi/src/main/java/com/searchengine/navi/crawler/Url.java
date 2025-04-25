package com.searchengine.navi.crawler;

import java.util.List;
public class Url {
    private String url;
    private double rank;
    private int urlsIn;
    private int urlsOut;
    private String baseUrl;

    public Url(String url, int rank) {
        this.url = url;
        this.rank = rank;
    }

    public Url(String url) {
        this.url = url;
        this.rank = 1;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public double getRank() {
        return rank;
    }

    public void incrementUrlsIn() {
        urlsIn++;
    }

    public void incrementUrlsOut() {
        urlsOut++;
    }

    public int getUrlsIn() {
        return urlsIn;
    }

    public int getUrlsOut() {
        return urlsOut;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public void calcRank(List<Url> parents) {
        for (Url parent : parents) {
            this.rank += parent.getRank() / parent.getUrlsOut();
        }
        this.rank = (1 - 0.85) + 0.85 * this.rank;
    }
    public String getBaseUrl() {
        return baseUrl;
    }
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean compareUrls (Url url) {
        if (this.url == url.getUrl()) {
            return true;
        }
        if (this.url == null || url.getUrl() == null) {
            return false;
        }
        return this.url.equals(url.getUrl());
    }
}
