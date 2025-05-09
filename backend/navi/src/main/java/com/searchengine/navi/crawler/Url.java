package com.searchengine.navi.crawler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
public class Url implements Comparable<Url>{
    private String url;
    private boolean isIndexed;
    private String content;
    private String title;
    private String hashingDoc;
    private String baseUrl;
    private List<String> parents;
    private List<String> children;
    private double rank;
    private String h1;
    private String h2;
    private long lastTime;
    private int depth;
    private String childTxt;
    public String getChildTxt() {
        return childTxt;
    }

    private String etag;
    private String lastModified;
    private boolean linkStructureChanged;

    
    public Url(String url) {
        this.url = url;
        this.rank = 1;
        this.depth = 0;
        this.isIndexed = false;
        this.content = "";
        this.title = "";
        this.hashingDoc = "";
        this.baseUrl = "";
        this.parents = new ArrayList<>();
        this.children = new ArrayList<>();
        this.h1 = "";
        this.h2 = "";
        this.lastTime = 0;
        // this.urlsIn = 0;
        // this.urlsOut = 0;
        this.childTxt = "";
        this.etag = "";
        this.lastModified = "";
        this.linkStructureChanged = false;
    }
    
    public String getEtag() {
        return etag;
    }
    
    public void setEtag(String etag) {
        this.etag = etag;
    }
    
    public String getLastModified() {
        return lastModified;
    }
    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }
    public boolean isLinkStructureChanged() {
        return linkStructureChanged;
    }

    public void clearChildren() {
        this.children.clear();
    }
    public void clearParents() {
        this.parents.clear();
    }
    public void setLinkStructureChanged(boolean structureChanged) {
        this.linkStructureChanged = structureChanged;
    }
    public void setRank(double rank) {
        this.rank = rank;
    }

    // private int urlsIn;
    // public void setUrlsIn(int urlsIn) {
    //     this.urlsIn = urlsIn;
    // }

    // private int urlsOut;
    // public void setUrlsOut(int urlsOut) {
    //     this.urlsOut = urlsOut;
    // }
    
    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
    public void setIndexed(boolean isIndexed) {
        this.isIndexed = isIndexed;
    }

    public boolean isIndexed() {
        return isIndexed;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getHashingDoc() {
        return hashingDoc;
    }

    public void setHashingDoc(String hashingDoc) {
        this.hashingDoc = hashingDoc;
    }

    public List<String> getParents() {
        return parents;
    }

    public void setParents(List<String> parents) {
        this.parents = parents != null ? new ArrayList<>(new HashSet<>(parents)) : new ArrayList<>(); // Ensure no duplicates when setting
    }

    public List<String> getChildren() {
        return children;
    }

    public void setChildren(List<String> children) {
        this.children = (children != null) ? new ArrayList<>(new HashSet<>(children)) : new ArrayList<>(); // Ensure no duplicates when setting
    }

    public void addParent(String parent) {
        if (!this.parents.contains(parent)) {
            this.parents.add(parent);
            // this.urlsIn++;
        }
    }

    public void addChild(String child) {
        if (!this.children.contains(child)) {
            this.children.add(child);
            // this.urlsOut++;
        }
    }

    public String getH1() {
        return h1;
    }

    public void setH1(String h1) {
        this.h1 = h1;
    }

    public String getH2() {
        return h2;
    }

    public void setH2(String h2) {
        this.h2 = h2;
    }


    public long getLastTime() {
        return lastTime;
    }
    
    public void setChildTxt(String childTxt) {
        this.childTxt = childTxt;
    }
    public String getChildTXt() {
        return childTxt;
    }
    public void setLastTime(long lastTime) {
        this.lastTime = lastTime;
    }

    public Url(String url, int rank) {
        this.url = url;
        this.rank = rank;
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

    // public void incrementUrlsIn() {
    //     urlsIn++;
    // }

    // public void incrementUrlsOut() {
    //     urlsOut++;
    // }

    // public void decrementUrlsIn() {
    //     urlsIn--;
    // }  
    // public void decrementUrlsOut() {
    //     urlsOut--;
    // }
    // public int getUrlsIn() {
    //     return urlsIn;
    // }

    // public int getUrlsOut() {
    //     return urlsOut;
    // }

    public void setRank(int rank) {
        this.rank = rank;
    }

    // public void calcRank(List<Url> parents) {
    //     for (Url parent : parents) {
    //         this.rank += parent.getRank() / parent.getUrlsOut();
    //     }
    //     this.rank = (1 - 0.85) + 0.85 * this.rank;
    // }
    public String getBaseUrl() {
        return baseUrl;
    }
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public int compareTo(Url o) {
        return Double.compare(this.rank, o.rank);
    }
}
