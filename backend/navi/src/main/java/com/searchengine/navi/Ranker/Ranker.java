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

    void BM25F() {
        HashMap<Integer, ArrayList<Double>> docFieldLength = new HashMap<>();

        // Loop over query terms
        for (String term : terms) {

            // Get postings of the term
            List<Document> postings = dbManager.getWordPostings(term);

            // Loop over each posting
            for (Document posting : postings) {
                int docId = posting.getInteger("docId");
                commonDocs.add(docId);

                // Loop over fields
                for (int i = 0; i < 4; i++) {
                    if (!docFieldLength.containsKey(docId) || docFieldLength.get(docId) == null) {
                        double length = dbManager.getFieldLengthPerDoc(docId, fields[i]);
                        docFieldLength.get(docId).add(length);
                    }
                    int TF = posting.getInteger(docFieldLength, i);

                    double score = calculateBM25FByTerm(term, docId);
                    scores.put(docId, scores.getOrDefault(docId, 0.0) + score);
                }
            }

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

        double count = dbManager.getAvgFieldLength("h1");
        double count2 = dbManager.getAvgFieldLength("h2");
        double count3 = dbManager.getAvgFieldLength("a");
        double count4 = dbManager.getAvgFieldLength("other");
        System.out.println("count h1: " + count);
        System.out.println("count h2: " + count2);
        System.out.println("count a: " + count3);
        System.out.println("count other: " + count4);
    }
}