package com.searchengine.navi.indexer;

import java.util.ArrayList;
import java.util.List;

public class Posting {
    private int docId;
    private int tf;
    private List<String> type;

    public Posting(int docId) {
        this.docId = docId;
        this.tf = 0;
        this.type = new ArrayList<>();
    }

    public void addPosition(String type) {
        this.type.add(type);
        this.tf++;
        // this.tf = positions.size();
    }

    public int getTF() {
        return tf;
    }

    public void setTF(int tf) {
        this.tf = tf;
    }

    public int getDocID() {
        return docId;
    }

    public List<String> getPos() {
        return type;
    }
}
