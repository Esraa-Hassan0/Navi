package com.searchengine.navi.Ranker;

import java.util.*;
import java.util.ArrayList;
import java.lang.Math;
import com.searchengine.dbmanager.DBManager;

public class Ranker {
    private double k = 1.5, b = 0.75;
    private double avgDocLength;
    private ArrayList<String> terms;

    static DBManager dbManager;

    public Ranker() {
        dbManager = new DBManager();
    }

    double calculateBM25(int IDF, int TF, int docLength) {
        double result = IDF * (TF * (k + 1) / (TF + k + (1 - b + b * (docLength / avgDocLength))));
        return result;
    }

    double getIDF(String word) {
        int n = dbManager.getDocumentsCount();
        int df = dbManager.getDF(word);

        double IDF = Math.log((n - df + 0.5) / (df + 0.5));
        return IDF;
    }

    double getAvgDocLength() {
        return 0;
    }

    public static void main(String args[]) {
        Ranker r = new Ranker();

        int count = dbManager.getDF("Google");
        System.out.println("Hi there");
        System.out.println(count);
    }
}