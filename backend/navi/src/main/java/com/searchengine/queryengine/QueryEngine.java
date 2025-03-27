package com.searchengine.queryengine;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import opennlp.tools.stemmer.PorterStemmer;

public class QueryEngine {

    private HashSet<String> stopWords;

    public List<String> parseQuery(String query) {
        addStopWords();
        String restructureText = query.toLowerCase().replaceAll("[^a-z\\s]", "");
        String[] arrList = restructureText.split("\\s+");
        List<String> listTokens = new ArrayList<>();
        PorterStemmer stemmer = new PorterStemmer();
        for (int i = 0; i < arrList.length; i++) {
            arrList[i] = stemmer.stem(arrList[i]);
        }
        for (String token : arrList) {
            if (!stopWords.contains(token)) {
                listTokens.add(token);
            }
        }
        return listTokens;
    }

    public void addStopWords() {
        stopWords = new HashSet<String>();
        try {
            BufferedReader scanner = new BufferedReader(new FileReader("./stopwords.txt"));
            String line;
            while ((line = scanner.readLine()) != null) {
                stopWords.add(line.trim());
            }
            scanner.close();
        } catch (IOException e) {
            System.out.println(e);
            return;
        }
    }
}
