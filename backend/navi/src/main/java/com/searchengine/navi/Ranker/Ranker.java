package com.searchengine.navi.Ranker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.lang.Math;
import com.searchengine.dbmanager.DBManager;
import com.searchengine.navi.queryengine.PhraseMatching;

import org.bson.Document;
import org.jsoup.Jsoup;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators.In;
import org.bson.types.ObjectId;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;

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

    String FIELD_COUNTS_PATH = "field_counts.json";

    Relevance relevance;
    Popularity popularity;

    // Phrase matching

    ArrayList<Object> phraseComponents = new ArrayList<>();

    static DBManager dbManager;

    public Ranker(ArrayList<String> queryTerms, ArrayList<Object> phraseComponents) {
        terms = queryTerms;
        dbManager = new DBManager();
        scores = new HashMap<>();
        relevanceScores = new HashMap<>();
        popularityScores = new HashMap<>();
        commonDocs = new HashSet<>();

        docsCount = dbManager.getDocumentsCount();
        System.out.println(GREEN + "Docs No.: " + docsCount + RESET);

        relevance = new Relevance();
        popularity = new Popularity();

        // phrase matching

        this.phraseComponents = phraseComponents;
    }

    void rank() {
        if (phraseComponents != null && !phraseComponents.isEmpty()) {
            if (phraseComponents.size() == 1 && phraseComponents.get(0) instanceof String) {
                // Single phrase query
                String phrase = (String) phraseComponents.get(0);
                System.out.println(PURPLE + "Ranking single phrase: " + phrase + RESET);
                rankPhrase(phrase, null, null);
            } else if (phraseComponents.stream()
                    .anyMatch(c -> c instanceof String && List.of("AND", "OR", "NOT").contains(c))) {
                // Boolean phrase query
                System.out.println(PURPLE + "Ranking Boolean phrase query" + RESET);
                rankBoolPhrase();
            } else {
                // Treat as term-based query
                System.out.println(PURPLE + "Ranking terms with BM25" + RESET);
                relevance.BM25F();
            }
        } else if (terms != null && !terms.isEmpty()) {
            // Term-based query
            System.out.println(PURPLE + "Ranking terms with BM25" + RESET);
            relevance.BM25F();
        } else {
            System.out.println(RED + "Error: No valid query provided" + RESET);
            return;
        }

        popularity.PageRank();

        for (ObjectId doc : commonDocs) {
            double score = relWeight * relevanceScores.getOrDefault(doc, 0.0)
                    + popWeight * popularityScores.getOrDefault(doc, 0.0);
            scores.put(doc, score);
        }
    }

    public List<ObjectId> sortDocs() {
        System.out.println("Tokens:");
        for (String term : terms) {
            System.out.println(term);
        }
        long startTime = System.nanoTime();

        rank();

        List<ObjectId> sortedDocs = new ArrayList<>(commonDocs);
        sortedDocs.sort((id1, id2) -> Double.compare(scores.get(id2), scores.get(id1)));

        long endTime = System.nanoTime();

        long durationInNano = endTime - startTime;
        double durationInMillis = durationInNano / 1_000_000.0;

        System.out.println("Time taken: " + durationInMillis + " ms");

        for (ObjectId doc : sortedDocs) {
            System.out.println(TEAL + "docId: " + doc + " score: " + scores.get(doc) +
                    RESET);
        }

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
            long startTime = System.nanoTime();

            Gson gson = new Gson();

            HashMap<String, Integer> fieldCounts;
            try (FileReader reader = new FileReader(FIELD_COUNTS_PATH)) {
                Type type = new TypeToken<HashMap<String, Integer>>() {
                }.getType();
                fieldCounts = gson.fromJson(reader, type);
                if (fieldCounts == null) {
                    fieldCounts = new HashMap<>();
                }
            } catch (IOException e) {
                System.err.println("Failed to load counts, starting empty: " +
                        e.getMessage());
                fieldCounts = new HashMap<>();
            }

            long endTime = System.nanoTime();

            long durationInNano = endTime - startTime;
            double durationInMillis = durationInNano / 1_000_000.0;

            System.out.println("Time taken in get all fields count: " + durationInMillis + " ms");

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
            if (!commonDocs.isEmpty()) {
                long startTime2 = System.nanoTime();

                docFieldLengths = dbManager.getFieldOccurrencesForDocs(new ArrayList<>(commonDocs));

                long endTime2 = System.nanoTime();

                long durationInNano2 = endTime2 - startTime2;
                double durationInMillis2 = durationInNano2 / 1_000_000.0;

                System.out.println("Time taken in fields occurences: " + durationInMillis2 + " ms");
            }

            long endTime = System.nanoTime();

            long durationInNano = endTime - startTime;
            double durationInMillis = durationInNano / 1_000_000.0;

            System.out.println("Time taken in initializing: " + durationInMillis + " ms");
        }

        void BM25F() {
            // HashMap<Integer, ArrayList<Integer>> docFieldLength = new HashMap<>();
            initializeRankParams();

            long startTime = System.nanoTime();

            // Loop over query terms
            for (String term : terms) {
                // System.out.println(YELLOW + "in term: " + term + RESET);
                Double IDF = IDFs.get(term);
                IDFs.put(term, IDF);

                if (IDF == null) {
                    continue;
                }

                // System.out.println(GREEN + "IDF: " + IDF + RESET);

                // Get postings of the term
                List<Document> postings = termPostings.get(term);

                // Loop over each posting
                for (Document posting : postings) {
                    ObjectId docId = posting.getObjectId("docID");

                    // System.out.print(RED + "in doc " + docId + RESET);

                    double totalScore = 0.0;

                    for (int i = 0; i < 4; i++) {
                        // System.out.print(PURPLE + "in fields" + RESET );

                        // System.out.print(
                        // GREEN + fields[i] + " length: " + docFieldLengths.get(docId).get(fields[i]) +
                        // RESET);

                        Document types = (Document) posting.get("types");

                        int TF = types.getInteger(fields[i], 0);
                        // System.out.print(GREEN + "TF: " + TF + RESET);

                        if (TF < 1) {
                            continue;
                        }

                        double score = calculateFieldScore(docFieldLengths.get(docId).get(fields[i]), TF, i, IDF);
                        totalScore += score;
                        // System.out.println(GREEN + "Score: " + score + RESET);

                    }
                    relevanceScores.put(docId, totalScore);
                }
            }
            long endTime = System.nanoTime();

            long durationInNano = endTime - startTime;
            double durationInMillis = durationInNano / 1_000_000.0;

            System.out.println("Time taken in ranking: " + durationInMillis + " ms");
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

                // System.out.println(GREEN + "DF: " + DF + RESET);

                IDFs.put(term, IDF);
            }
        }
    }

    class Popularity {
        void PageRank() {
            long startTime = System.nanoTime();

            popularityScores = dbManager.getPageRanks(new ArrayList<>(commonDocs));
            // for (ObjectId docId : commonDocs) {
            // // System.out.println(GREEN + "doc: " + docId + " pageRank: " +
            // // popularityScores.get(docId) + RESET);
            // }
            long endTime = System.nanoTime();

            long durationInNano = endTime - startTime;
            double durationInMillis = durationInNano / 1_000_000.0;

            System.out.println("Time taken in retrieving page rank: " + durationInMillis + " ms");
        }

    }

    // this funcction has to fill relevanceScores and commonDocs
    void rankPhrase(String phrase, HashMap<ObjectId, Double> targetScores, HashSet<ObjectId> targetDocs) {
        // Default to class-level collections if null
        HashMap<ObjectId, Double> scores = (targetScores != null) ? targetScores : relevanceScores;
        HashSet<ObjectId> resultDocs = (targetDocs != null) ? targetDocs : commonDocs;

        String regex = new PhraseMatching().BuildStringRegex(phrase);
        Pattern pattern = Pattern.compile(regex);

        // Define weights
        Map<String, Double> fieldWeights = Map.of(
                "h1", 2.5,
                "h2", 2.0,
                "a", 1.5,
                "body", 1.0 // fallback field
        );

        ArrayList<Document> docs = dbManager.getDocumentsContainingPhrase(regex);

        for (Document doc : docs) {
            ObjectId docId = doc.getObjectId("_id");

            if (docId == null) {
                continue;
            }
            // To be replaced after hagar put them in the database
            try {

                double score = 0.0;

                // Debug output

                for (Map.Entry<String, Double> entry : fieldWeights.entrySet()) {
                    String tag = entry.getKey();
                    double weight = entry.getValue();

                    String fieldText = tag.equals("body") ? doc.getString("content") : doc.getString(tag);
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
                    scores.put(docId, score);
                    resultDocs.add(docId);
                }

            } catch (Exception e) {
                // System.out.println(RED + "Error fetching HTML for URL: " + url + " — " +
                // e.getMessage() + RESET);
            }
        }
    }

    // Rank boolean operators

    void rankBoolPhrase() {
        // Step 1: Validate query
        if (phraseComponents == null || phraseComponents.isEmpty()) {
            System.out.println(RED + "Error: Empty phrase components" + RESET);
            return;
        }

        List<Map<ObjectId, Double>> phraseScores = new ArrayList<>();
        List<Set<ObjectId>> phraseDocs = new ArrayList<>();
        int phraseCount = (int) phraseComponents.stream()
                .filter(c -> c instanceof String && !List.of("AND", "OR", "NOT").contains(c))
                .count();

        if (phraseCount > 3) {
            System.out.println(RED + "Error: Maximum of 3 phrases allowed" + RESET);
            return;
        }

        // Step 2: Process each phrase
        HashMap<ObjectId, Double> tempRelevanceScores = new HashMap<>();
        HashSet<ObjectId> tempCommonDocs = new HashSet<>();
        Map<Integer, String> phraseMap = new HashMap<>();
        int currentIndex = 0;

        for (Object component : phraseComponents) {
            if (component instanceof String && !List.of("AND", "OR", "NOT").contains((String) component)) {
                String phrase = ((String) component).trim();
                tempRelevanceScores.clear();
                tempCommonDocs.clear();
                rankPhrase(phrase, tempRelevanceScores, tempCommonDocs);
                phraseScores.add(new HashMap<>(tempRelevanceScores));
                phraseDocs.add(new HashSet<>(tempCommonDocs));
                phraseMap.put(currentIndex, phrase);
                System.out
                        .println(PURPLE + "Phrase '" + phrase + "' matches " + tempCommonDocs.size() + " docs" + RESET);
                currentIndex++;
            }
        }

        // Step 3: Normalize components
        List<Object> normalized = new ArrayList<>();
        int index = 0;
        for (int i = 0; i < phraseComponents.size(); i++) {
            Object component = phraseComponents.get(i);
            if (component instanceof String) {
                String str = (String) component;
                if (str.equals("NOT")) {
                    if (i + 1 >= phraseComponents.size() || !(phraseComponents.get(i + 1) instanceof String)
                            || List.of("AND", "OR", "NOT").contains((String) phraseComponents.get(i + 1))) {
                        System.out.println(RED + "Invalid query: NOT must be followed by a phrase" + RESET);
                        return;
                    }
                    normalized.add(new AbstractMap.SimpleEntry<>("NOT", index));
                    i++;
                    index++;
                } else if (List.of("AND", "OR").contains(str)) {
                    normalized.add(str);
                } else {
                    normalized.add(index++);
                }
            }
        }
        System.out.println(PURPLE + "Normalized components: " + normalized + RESET);

        // Step 4: Resolve NOTs
        List<Set<ObjectId>> resolvedDocs = new ArrayList<>(phraseDocs);
        List<Map<ObjectId, Double>> resolvedScores = new ArrayList<>(phraseScores);
        HashSet<ObjectId> universeDocs = dbManager.getAllDocumentIds();

        for (int i = 0; i < normalized.size(); i++) {
            Object component = normalized.get(i);
            if (component instanceof Map.Entry) {
                @SuppressWarnings("unchecked")
                Map.Entry<String, Integer> entry = (Map.Entry<String, Integer>) component;
                if ("NOT".equals(entry.getKey())) {
                    int idx = entry.getValue();
                    Set<ObjectId> negatedDocs = new HashSet<>(universeDocs);
                    negatedDocs.removeAll(phraseDocs.get(idx));
                    resolvedDocs.set(idx, negatedDocs);
                    Map<ObjectId, Double> negatedScores = new HashMap<>();
                    for (ObjectId docId : negatedDocs) {
                        negatedScores.put(docId, 0.1);
                    }
                    resolvedScores.set(idx, negatedScores);
                    normalized.set(i, idx);
                    System.out.println(PURPLE + "After NOT on index " + idx + ", docs: " + negatedDocs.size() + RESET);
                }
            }
        }

        // Step 5: Evaluate with precedence (NOT > AND > OR)
        if (normalized.isEmpty()) {
            System.out.println(RED + "Invalid query: Empty normalized components" + RESET);
            return;
        }

        Set<ObjectId> resultDocs = new HashSet<>();
        Map<ObjectId, Double> resultScores = new HashMap<>();
        boolean startsWithNot = phraseComponents.get(0).equals("NOT");

        // Process OR/AND first, then apply NOT
        int i = 0;
        if (startsWithNot) {
            if (normalized.get(0) instanceof Integer) {
                int idx = (Integer) normalized.get(0);
                resultDocs.addAll(resolvedDocs.get(idx));
                resultScores.putAll(resolvedScores.get(idx));
                i = 1;
            } else {
                System.out.println(RED + "Invalid query: NOT must be followed by a phrase index" + RESET);
                return;
            }
        } else if (normalized.get(0) instanceof Integer) {
            int idx = (Integer) normalized.get(0);
            resultDocs.addAll(resolvedDocs.get(idx));
            resultScores.putAll(resolvedScores.get(idx));
            i = 1;
        } else {
            System.out.println(RED + "Invalid query: Must start with a phrase or NOT" + RESET);
            return;
        }

        // Handle OR and AND
        while (i < normalized.size() - 1) {
            if (!(normalized.get(i) instanceof String) || !(normalized.get(i + 1) instanceof Integer)) {
                System.out.println(RED + "Invalid query structure at position " + i + ": " + normalized + RESET);
                return;
            }
            String operator = (String) normalized.get(i);
            int nextIdx = (Integer) normalized.get(i + 1);
            Set<ObjectId> nextDocs = resolvedDocs.get(nextIdx);
            Map<ObjectId, Double> nextScores = resolvedScores.get(nextIdx);

            if ("OR".equals(operator)) {
                for (ObjectId docId : nextDocs) {
                    double score = nextScores.getOrDefault(docId, 0.0);
                    if (resultDocs.contains(docId)) {
                        double existingScore = resultScores.getOrDefault(docId, 0.0);
                        score = Math.max(existingScore, score);
                    }
                    resultDocs.add(docId);
                    resultScores.put(docId, score);
                }
                System.out.println(PURPLE + "After OR, resultDocs: " + resultDocs.size() + RESET);
            } else if ("AND".equals(operator)) {
                Set<ObjectId> newResultDocs = new HashSet<>();
                Map<ObjectId, Double> newScores = new HashMap<>();
                for (ObjectId docId : resultDocs) {
                    if (nextDocs.contains(docId)) {
                        double score1 = resultScores.getOrDefault(docId, 0.0);
                        double score2 = nextScores.getOrDefault(docId, 0.0);
                        if (score1 > 0 && score2 > 0) {
                            newScores.put(docId, (score1 + score2) / 2.0);
                            newResultDocs.add(docId);
                        }
                    }
                }
                resultDocs.clear();
                resultDocs.addAll(newResultDocs);
                resultScores.clear();
                resultScores.putAll(newScores);
                System.out.println(PURPLE + "After AND, resultDocs: " + resultDocs.size() + RESET);
            }
            i += 2;
        }

        // Apply trailing NOT
        if (i < normalized.size() && normalized.get(i) instanceof Integer) {
            int notIdx = (Integer) normalized.get(i);
            resultDocs.removeAll(resolvedDocs.get(notIdx));
            Map<ObjectId, Double> newScores = new HashMap<>();
            for (ObjectId docId : resultDocs) {
                newScores.put(docId, resultScores.getOrDefault(docId, 0.0));
            }
            resultScores.clear();
            resultScores.putAll(newScores);
            System.out.println(PURPLE + "After NOT, resultDocs: " + resultDocs.size() + RESET);
        }

        // Step 6: Assign final results
        commonDocs.clear();
        commonDocs.addAll(resultDocs);
        relevanceScores.clear();
        relevanceScores.putAll(resultScores);

        if (resultDocs.isEmpty()) {
            System.out.println(RED + "No documents satisfy the Boolean query" + RESET);
        }
    }

    public static void main(String args[]) {
        ArrayList<String> terms = new ArrayList<>();
        // terms.add("alyaa");
        // terms.add("hi");
        // terms.add("hey");
        terms.add("polici");
        // terms.add("these");
        // terms.add("term");
        ArrayList<Object> queryComponents2 = new ArrayList<>();

        // queryComponents2.add("NOT");
        // queryComponents2.add("Learn more about the LinkedIn");
        // // queryComponents2.add("OR");
        // queryComponents2.add("Privacy & Terms Google Terms of Service Terms Your
        // relationship with");
        // queryComponents2.add("OR");
        // queryComponents2.add(" Learn more about the LinkedIn Professional
        // Community");
        // System.out.println("terms");
        // for (Object object : terms) {
        // System.out.println(object);
        // }
        // System.out.println("queryComponents2");
        // for (Object object : queryComponents2) {
        // System.out.println(object);
        // }
        Ranker r = new Ranker(terms, queryComponents2);

        List<ObjectId> docs = r.sortDocs();

        System.out.println("Results No.: " + docs.size());

        // for (ObjectId doc : docs) {
        // System.out.println(doc + "=================");
        // }

    }
}