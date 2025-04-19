package com.searchengine.navi.Ranker;

import java.util.*;
import java.lang.Math;
import com.searchengine.dbmanager.DBManager;

import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators.In;

public class Ranker {
    static final String RESET = "\u001B[0m";
    static final String TEAL = "\u001B[36m";
    static final String YELLOW = "\u001B[33m";
    static final String RED = "\u001B[31m";
    static final String GREEN = "\u001B[32m";
    static final String PURPLE = "\u001B[35m";
    private double k = 1.5, b = 0.75;
    private double[] avgDocFieldsLengths;
    private String[] fields = { "h1", "h2", "a", "other" };
    private double[] weight = { 2.5, 2, 1.5, 1 };
    private int docsCount;
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

    public Ranker(ArrayList<String> queryTerms) {
        terms = queryTerms;
        dbManager = new DBManager();
        scores = new HashMap<>();
        commonDocs = new HashSet<>();
        avgDocFieldsLengths = new double[4];
        for (int i = 0; i < 4; i++) {
            avgDocFieldsLengths[i] = dbManager.getAvgFieldLength(fields[i]);
        }
        docsCount = dbManager.getDocumentsCount();
        System.out.println(GREEN + "Docs No.: " + docsCount + RESET);
    }
    
    public List<Integer> sortDocs() {
        List<Integer> sortedDocs = new ArrayList<>(commonDocs);
        sortedDocs.sort((id1, id2) -> Double.compare(scores.get(id2), scores.get(id1)));
        return sortedDocs;
    }

    void BM25F() {
        HashMap<Integer, ArrayList<Integer>> docFieldLength = new HashMap<>();

        // Loop over query terms
        for (String term : terms) {
            System.out.println(YELLOW + "in terms" + RESET);

            double IDF = getIDF(term);
            System.out.println(GREEN + "IDF: " + IDF + RESET);

            // Get postings of the term
            List<Document> postings = dbManager.getWordPostings(term);

            // Loop over each posting
            for (Document posting : postings) {
                int docId = posting.getInteger("docID");

                commonDocs.add(docId);
                System.out.println(RED + "in doc " + docId + RESET);

                // Loop over fields
                boolean calculated = true;
                if (!docFieldLength.containsKey(docId) || docFieldLength.get(docId) == null) {
                    calculated = false;
                    ArrayList<Integer> lengths = new ArrayList<>();
                    docFieldLength.put(docId, lengths);
                }
                for (int i = 0; i < 4; i++) {
                    System.out.println(PURPLE + "in fields" + RESET);

                    if (!calculated) {
                        int length = dbManager.getFieldLengthPerDoc(docId, fields[i]);
                        docFieldLength.get(docId).add(length);
                    }
                    System.out.println(GREEN + fields[i] + " length: " + docFieldLength.get(docId).get(i) + RESET);

                    Document types = (Document) posting.get("types");

                    int TF = types.getInteger(fields[i], 0);
                    System.out.println(GREEN + "TF: " + TF + RESET);

                    double score = calculateBM25FByTerm(term, docFieldLength.get(docId).get(i), TF, i, IDF);
                    System.out.println(GREEN + "Score: " + score + RESET);

                    scores.put(docId, scores.getOrDefault(docId, 0.0) + score);
                }
            }
        }
    }

    double calculateBM25FByTerm(String word, int docLength, int TF, int field, double IDF) {
        double result = 0;
        result = weight[field] * IDF * (TF * (k + 1) / (TF + k * (1 - b + b * (docLength
                / avgDocFieldsLengths[field]))));
        return result;
    }

    public void test() {
        BM25F();
        for (int doc : commonDocs) {
            System.out.println("docId: " + doc + "score: " + scores.get(doc));
        }
    }

    double getIDF(String word) {
        int DF = dbManager.getDF(word);

        System.out.println(GREEN + "DF: " + DF + RESET);

        double IDF = Math.log10((docsCount - DF + 0.5) / (DF + 0.5));
        return IDF;
    }

    double getAvgFieldLength() {
        return 0;
    }

    double getDocPopularity() {
        return 0;
    }

    public static void main(String args[]) {
        ArrayList<String> terms = new ArrayList<>();
        terms.add("your");
        terms.add("branch");
        Ranker r = new Ranker(terms);

        r.test();
        List<Integer> docs = r.sortDocs();
        for (int doc : docs) {
            System.out.println(doc);
        }
        // double count = dbManager.getAvgFieldLength("h1");
        // double count2 = dbManager.getAvgFieldLength("h2");
        // double count3 = dbManager.getAvgFieldLength("a");
        // double count4 = dbManager.getAvgFieldLength("other");
        // System.out.println("count h1: " + count);
        // System.out.println("count h2: " + count2);
        // System.out.println("count a: " + count3);
        // System.out.println("count other: " + count4);
    }
}