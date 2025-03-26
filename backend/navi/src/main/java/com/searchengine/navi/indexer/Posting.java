package com.searchengine.navi.indexer;

import java.util.ArrayList;
import java.util.List;

public class Posting {
    private int docId;
    private int tf;
    private List<Position> positions;

    public Posting(int docId) {
        this.docId = docId;
        this.tf = 0; // Initialize tf to 0, increment as positions are added
        this.positions = new ArrayList<>();
    }

    public void addPosition(Position position) {
        this.positions.add(position);
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

    public List<Position> getPos() {
        return positions;
    }
}
