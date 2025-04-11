package com.searchengine.navi.Ranker;

import java.util.*;
import java.util.ArrayList;
import java.lang.Math;
import com.searchengine.dbmanager.DBManager;
import java.util.HashMap;
import java.util.HashSet;

public class Ranker {
    private double k = 1.5, b = 0.75;
    private double[] avgDocFieldsLengths;
    private double avgDocLength;
    private String[] fields = { "h1", "h2", "a", "other" };
    private ArrayList<String> terms;
    private HashMap<Integer, Double> scores;
    private HashSet<Integer> commonDocs;

    static DBManager dbManager;

    public Ranker() {
        dbManager = new DBManager();
        avgDocFieldsLengths = new double[4];
        for (int i = 0; i < 4; i++) {
            avgDocFieldsLengths[i] = dbManager.getAvgFieldLength(fields[i]);
        }
    }

    void BM25() {
        int size = terms.size();
        for (int i = 0; i < size; i++) {
            // get the docs ids from array of postings
        }
    }

    double calculateBM25Score(int IDF, int TF, int docLength) {
        double result = IDF * (TF * (k + 1) / (TF + k + (1 - b + b * (docLength / avgDocLength))));
        return result;
    }

    double getIDF(String word) {
        int n = dbManager.getDocumentsCount();
        int df = dbManager.getDF(word);

        double IDF = Math.log((n - df + 0.5) / (df + 0.5));
        return IDF;
    }

    double getAvgFieldLength() {
        return 0;
    }

    double getDocPopularity() {
        return 0;
    }

    public static void main(String args[]) {
        Ranker r = new Ranker();

        double count = dbManager.getAvgFieldLength("other");
        double count3 = dbManager.getAvgFieldLength("h1");
        double count2 = dbManager.getAvgFieldLength("h2");
        System.out.println("Hi there");
        System.out.println(count);
        System.out.println(count2);
        System.out.println(count3);
    }
}