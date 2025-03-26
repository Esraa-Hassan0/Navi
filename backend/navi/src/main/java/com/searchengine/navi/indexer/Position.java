package com.searchengine.navi.indexer;

public class Position {
    private String type;
    private int pos;

    public Position(String type, int pos) {
        this.type = type;
        this.pos = pos;
    }

    // Getters
    public String getType() {
        return type;
    }

    public int getPos() {
        return pos;
    }

    public void setPos(int pos) {
        this.pos = pos;
    }



    @Override
    public String toString() {
        return "Position{" +
                "type='" + type + '\'' +
                ", pos=" + pos +
                '}';
    }
}
