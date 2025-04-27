package com.searchengine.dbmanager;

import org.bson.types.ObjectId;
// import org.bson.Document;
import org.jsoup.nodes.Document;

public class Test {
    public static void main(String[] args) {
        final String ANSI_RESET = "\u001B[0m";
        final String ANSI_GREEN = "\u001B[32m";

        System.out.println(ANSI_GREEN);
        try {
            DBManager dbManager = new DBManager();
            Document doc = org.jsoup.Jsoup.connect("https://toolsfairy.com/code-test/sample-html-files#").get();
            String url = doc.location();
            ObjectId docID = dbManager.retrieveDocID(url);
            System.out.println(docID);
            dbManager.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(ANSI_RESET);
    }
}