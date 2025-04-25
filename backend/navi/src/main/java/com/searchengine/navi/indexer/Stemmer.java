package com.searchengine.navi.indexer;

import org.tartarus.snowball.ext.englishStemmer;

public class Stemmer {

    private englishStemmer stemmer;

    public Stemmer() {
        this.stemmer = new englishStemmer(); // You can initialize a specific stemmer for English
    }

    public String stemWord(String word) {
        stemmer.setCurrent(word);
        stemmer.stem();
        return stemmer.getCurrent();
    }
}
