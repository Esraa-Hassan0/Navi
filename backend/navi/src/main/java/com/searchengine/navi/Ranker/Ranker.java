package com.searchengine.navi.Ranker;

import java.util.*;
import java.util.ArrayList;

public class Ranker {
    private double k = 1.5, b = 0.75;
    private double avgDocLength;
    private ArrayList<String> terms;

    public Ranker() {

    }

    double calculateBM25(int IDF, int TF, int docLength) {
        double result = IDF * (TF * (k + 1) / (TF + k + (1 - b + b * (docLength / avgDocLength))));
        return result;
    }

    double getAvgDocLength() {
        return 0;
    }
}