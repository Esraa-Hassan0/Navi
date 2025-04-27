package com.searchengine.navi.Ranker;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.Math;
import com.searchengine.dbmanager.DBManager;
import com.searchengine.navi.queryengine.PhraseMatching;

import org.bson.Document;
import org.jsoup.Jsoup;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators.In;
import org.bson.types.ObjectId;

public class Ranker {
    static final String RESET = "\u001B[0m";
    static final String TEAL = "\u001B[36m";
    static final String YELLOW = "\u001B[33m";
    static final String RED = "\u001B[31m";
    static final String GREEN = "\u001B[32m";
    static final String PURPLE = "\u001B[35m";
    private int docsCount;
    private ArrayList<String> terms;
    private HashMap<ObjectId, Double> scores;
    private HashMap<ObjectId, Double> relevanceScores;
    private HashMap<ObjectId, Double> popularityScores;
    private HashSet<ObjectId> commonDocs;
    private double relWeight = 0.7;
    private double popWeight = 0.3;

    // Phrase matching

    private boolean isPhrase = false;
    private String phrase = "";

    static DBManager dbManager;

    public Ranker(ArrayList<String> queryTerms, String phrase, boolean isPhrase) {
        terms = queryTerms;
        dbManager = new DBManager();
        scores = new HashMap<>();
        relevanceScores = new HashMap<>();
        popularityScores = new HashMap<>();
        commonDocs = new HashSet<>();

        docsCount = dbManager.getDocumentsCount();
        System.out.println(GREEN + "Docs No.: " + docsCount + RESET);

        // phrase matching

        this.phrase = phrase;
        this.isPhrase = isPhrase;
    }

    void rank() {
        Relevance relevance = new Relevance();
        Popularity popularity = new Popularity();

        if (isPhrase) {
            // rankPhrase(phrase);
        } else {
            relevance.BM25F();
        }

        popularity.PageRank();

        for (ObjectId doc : commonDocs) {
            double score = relWeight * relevanceScores.getOrDefault(doc, 0.0)
                    + popWeight * popularityScores.getOrDefault(doc, 0.0);
            scores.put(doc, score);
        }
        for (ObjectId doc : commonDocs) {
            System.out.println(TEAL + "docId: " + doc + " score: " + scores.get(doc) + RESET);
        }
    }

    public List<ObjectId> sortDocs() {
        long startTime = System.nanoTime();

        rank();
        List<ObjectId> sortedDocs = new ArrayList<>(commonDocs);
        sortedDocs.sort((id1, id2) -> Double.compare(scores.get(id2), scores.get(id1)));

        long endTime = System.nanoTime();

        long durationInNano = endTime - startTime;
        double durationInMillis = durationInNano / 1_000_000.0;

        System.out.println("Time taken: " + durationInMillis + " ms");
        return sortedDocs;
    }

    class Relevance {
        private double k = 1.5, b = 0.75;
        private double[] avgDocFieldsLengths;
        private String[] fields = { "h1", "h2", "a", "other" };
        private double[] weight = { 2.5, 2, 1.5, 1 };
        HashMap<String, Double> IDFs;
        private HashMap<String, List<Document>> termPostings;
        Map<ObjectId, Map<String, Integer>> docFieldLengths;

        Relevance() {
            IDFs = new HashMap<>();
            avgDocFieldsLengths = new double[4];
            HashMap<String, Integer> fieldCounts = dbManager.getAllFieldsCount();

            for (int i = 0; i < 4; i++) {
                avgDocFieldsLengths[i] = fieldCounts.getOrDefault(fields[i], 0) / (double) docsCount;
            }
        }

        void initializeRankParams() {
            long startTime = System.nanoTime();

            getIDFs();
            termPostings = dbManager.getWordsPostings(terms);

            for (String term : terms) {
                List<Document> postings = termPostings.get(term);

                // Add documents in common documents
                for (Document posting : postings) {
                    ObjectId docId = posting.getObjectId("docID");

                    commonDocs.add(docId);
                }
            }

            // Calculate all docsFieldLengths
            docFieldLengths = dbManager.getFieldOccurrencesForDocs(new ArrayList<>(commonDocs));

            for (ObjectId doc : commonDocs) {
                for (String field : fields) {
                    System.out.println("doc " + field + " length: " + docFieldLengths.get(doc).get(field));
                }
            }
            long endTime = System.nanoTime();

            long durationInNano = endTime - startTime;
            double durationInMillis = durationInNano / 1_000_000.0;

            System.out.println("Time taken: " + durationInMillis + " ms");
        }

        void BM25F() {
            // HashMap<Integer, ArrayList<Integer>> docFieldLength = new HashMap<>();
            initializeRankParams();

            // Loop over query terms
            for (String term : terms) {
                System.out.println(YELLOW + "in terms" + RESET);
                Double IDF = IDFs.get(term);
                IDFs.put(term, IDF);

                if (IDF == null) {
                    continue;
                }

                System.out.println(GREEN + "IDF: " + IDF + RESET);

                // Get postings of the term
                List<Document> postings = termPostings.get(term);

                // Loop over each posting
                for (Document posting : postings) {
                    ObjectId docId = posting.getObjectId("docID");

                    commonDocs.add(docId);
                    System.out.println(RED + "in doc " + docId + RESET);

                    double totalScore = 0.0;

                    for (int i = 0; i < 4; i++) {
                        System.out.println(PURPLE + "in fields" + RESET);

                        System.out.println(
                                GREEN + fields[i] + " length: " + docFieldLengths.get(docId).get(fields[i]) + RESET);

                        Document types = (Document) posting.get("types");

                        int TF = types.getInteger(fields[i], 0);
                        System.out.println(GREEN + "TF: " + TF + RESET);

                        if (TF < 1) {
                            continue;
                        }

                        double score = calculateFieldScore(docFieldLengths.get(docId).get(fields[i]), TF, i, IDF);
                        totalScore += score;
                        System.out.println(GREEN + "Score: " + score + RESET);

                    }
                    relevanceScores.put(docId, totalScore);
                }
            }
        }

        double calculateFieldScore(int docLength, int TF, int field, double IDF) {
            double score = weight[field] * IDF * (TF * (k + 1) / (TF + k * (1 - b + b * (docLength
                    / avgDocFieldsLengths[field]))));
            return score;
        }

        void getIDFs() {
            HashMap<String, Integer> DFs = dbManager.getDFs(terms);

            for (String term : terms) {
                int DF = DFs.get(term);
                Double IDF;
                if (DF == -1) {
                    IDF = null;
                } else {
                    IDF = Math.log10((docsCount - DF + 0.5) / (DF + 0.5));
                }

                System.out.println(GREEN + "DF: " + DF + RESET);

                IDFs.put(term, IDF);
            }
        }
    }

    class Popularity {
        void PageRank() {
            popularityScores = dbManager.getPageRanks(new ArrayList<>(commonDocs));
            for (ObjectId docId : commonDocs) {
                System.out.println(GREEN + "doc: " + docId + " pageRank: " + popularityScores.get(docId) + RESET);
            }
        }
    }

    // this funcction has to fill relevanceScores and commonDocs
    void rankPhrase(String phrase) {

        String regex = new PhraseMatching().BuildStringRegex(phrase);
        Pattern pattern = Pattern.compile(regex);

        // Define weights
        Map<String, Double> fieldWeights = Map.of(
                "h1", 2.5,
                "h2", 2.0,
                "a", 1.5,
                "body", 1.0 // fallback field
        );

        ArrayList<Document> docs = dbManager.getDocumentsContent();

        for (Document doc : docs) {
            ObjectId docId = doc.getObjectId("_id");
            String url = doc.getString("url");

            if (docId == null || url == null || url.isEmpty()) {
                continue;
            }
            // To be replaced after hagar put them in the database
            try {
                org.jsoup.nodes.Document jsoupDoc = Jsoup.connect(url).get(); // fetch HTML from URL
                double score = 0.0;

                // Debug output
                System.out.println(PURPLE + jsoupDoc.text() + " ==========TEXT===========" +
                        RESET);
                System.out.println(GREEN + jsoupDoc.select("h1,h2").text() + "=========H1============" + RESET);

                for (Map.Entry<String, Double> entry : fieldWeights.entrySet()) {
                    String tag = entry.getKey();
                    double weight = entry.getValue();

                    String fieldText = tag.equals("body") ? jsoupDoc.body().text() : jsoupDoc.select(tag).text();
                    fieldText = fieldText.toLowerCase().trim().replaceAll("\\s+", " ");

                    Matcher matcher = pattern.matcher(fieldText);
                    int freq = 0;
                    while (matcher.find()) {
                        freq++;
                    }

                    String[] fieldWords = fieldText.split("\\s+");
                    int fieldLength = fieldWords.length;

                    // If the field length is greater than 0, compute score
                    if (fieldLength > 0) {
                        double fieldScore = weight * ((double) freq / fieldLength);
                        score += fieldScore;
                    }
                }

                if (score > 0.0) {
                    relevanceScores.put(docId, score);
                    commonDocs.add(docId);
                }

            } catch (Exception e) {
                System.out.println(RED + "Error fetching HTML for URL: " + url + " — " +
                        e.getMessage() + RESET);
            }
        }
    }

    public static void main(String args[]) {
        ArrayList<String> terms = new ArrayList<>();
        terms.add("alyaa");
        terms.add("hi");
        terms.add("hey");
        terms.add("lolo");
        Ranker r = new Ranker(terms, "hi there", false);

        List<ObjectId> docs = r.sortDocs();
        for (ObjectId doc : docs) {
            System.out.println(doc);
        }

    }
}