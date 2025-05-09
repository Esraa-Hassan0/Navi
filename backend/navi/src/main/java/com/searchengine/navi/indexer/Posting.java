package com.searchengine.navi.indexer;

import java.util.HashMap;
import java.util.Map;

import org.bson.types.ObjectId;

public class Posting {
    private ObjectId docId;
    private int tf;
    private Map<String, Integer> type;

    public Posting(ObjectId docId) {
        this.docId = docId;
        this.tf = 0;
        this.type = new HashMap<>();
    }

    public void addPosition(String type) {
        this.tf++;

        this.type.put(type, this.type.getOrDefault(type, 0) + 1);
    }

    public int getTF() {
        return tf;
    }

    public void setTF(int tf) {
        this.tf = tf;
    }

    public ObjectId getDocID() {
        return docId;
    }

    public Map<String, Integer> getTypeCounts() {
        return type;
    }
}
