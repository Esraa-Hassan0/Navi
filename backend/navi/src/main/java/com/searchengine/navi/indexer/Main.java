package com.searchengine.navi.indexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.bson.Document;
import org.jsoup.Jsoup;

import com.searchengine.dbmanager.DBManager;
import com.searchengine.navi.indexer.Indexer.Token;

//Loop over docs, pass it to the tokenize doc   => done

//put the map to the db 

//clean the code: dependencies

//incremental update 
//multi threading

public class Main {
    static DBManager db = new DBManager();
    static Indexer indexer = new Indexer();

    public static void main(String[] args) {
        // Retrieve urls from db ->doc
        ArrayList<Document> urls = db.retriveURLs();
        HashMap<String, Token> invertedIndex = new HashMap<>();

        // Loop over them and call tokenize
        for (Document doc : urls) {
            String url = doc.getString("url");

            try {
                org.jsoup.nodes.Document document = Jsoup.connect(url).get();

                // System.out.println(document.text());
                try {
                    indexer.tokenizeDocument(document, invertedIndex);
                    System.out.println(url);

                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

        // Insert inverted index to the db

        insertIntoInvertedIndex(invertedIndex);

        // Print for debugging issues

        for (String word : invertedIndex.keySet()) {
            Token token = invertedIndex.get(word);
            for (Posting posting : token.getPostings()) {
                System.out.print("Word: " + word + " -> DocId: " +
                        posting.getDocID() + ", TF: " + posting.getTF() + ", Positions: [");
                // Print all positions
                for (int i = 0; i < posting.getPos().size(); i++) {
                    Position pos = posting.getPos().get(i);
                    System.out.print("Pos: " + pos.getPos() + " (Type: " + pos.getType() + ")");
                    if (i < posting.getPos().size() - 1) {
                        System.out.print(", ");
                    }
                }
                System.out.println("]");
            }
        }

    }

    public static void insertIntoInvertedIndex(HashMap<String, Token> invertedIndex) {
        db.insertIntoInvertedIndex(invertedIndex);

    }

}
