package com.searchengine.navi.crawler;

public class Page {
    private Url url;
    private boolean isIndexed;
    private String content;
    private String title;
    private String hashingDoc;

    public Page(Url url) {
        this.url = url;
        this.isIndexed = false;
        this.content = "";
        this.title = "";
        this.hashingDoc = "";
    }

    public Url getUrl() {
        return url;
    }
    public void setUrl(Url url) {
        this.url = url;
    }
    public boolean isIndexed() {
        return isIndexed;
    }
    public void setIndexed(boolean isIndexed) {
        this.isIndexed = isIndexed;
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
  
}
