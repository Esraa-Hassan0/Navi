package com.searchengine.navi.Ranker;

import java.util.*;
import java.lang.Math;
import com.searchengine.dbmanager.DBManager;

import org.bson.Document;

public class Ranker {
    private double k = 1.5, b = 0.75;
    private double[] avgDocFieldsLengths;
    private String[] fields = { "h1", "h2", "a", "other" };
    private double[] weight = { 2.5, 2, 1.5, 1 };
    private ArrayList<String> terms;
    private HashMap<Integer, Double> scores;
    private HashSet<Integer> commonDocs;

    static DBManager dbManager;

    enum Field {
        h1,
        h2,
        a,
        other
    }

    public Ranker() {
        dbManager = new DBManager();
        avgDocFieldsLengths = new double[4];
        for (int i = 0; i < 4; i++) {
            avgDocFieldsLengths[i] = dbManager.getAvgFieldLength(fields[i]);
        }
    }

    void BM25() {
        int size = terms.size();
        HashMap<Integer, ArrayList<Double>> docFieldLength;
        for (int i = 0; i < size; i++) {
            // get the docs ids from array of postings
            // for each doc: calculate TF and docfieldlength
        }
    }

    double calculateBM25FByTerm(String word, int docId) {
        double IDF = getIDF(word);
        double result = 0;
        for (int i = 0; i < 4; i++) {
            // result += weight[i] * IDF * (TF * (k + 1) / (TF + k + (1 - b + b * (docLength
            // / avgDocFieldsLengths[i]))));
        }

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

        String searchWord = "blah";
        List<Document> postings = dbManager.getWordPostings(searchWord);

        System.out.println("DocIds and TFs for '" + searchWord + "':");
        postings.forEach(posting -> System.out.println("docId: " + posting.getInteger("docId") +
                ", TF: " + posting.getInteger("TF")));
    }
}