// hash docs to check for duplicates
package com.searchengine.navi.crawler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

// import org.apache.commons.codec.digest.DigestUtils;

public class HashingManager {
    HashMap<String, String> hashDoc;
    Set<String> hashSet;

    public HashingManager(HashMap<String, String> hashDoc, Set<String> hashSet) {
        this.hashDoc = hashDoc;
        this.hashSet = hashSet;
    }

    public void addHashDoc(String doc) {
        String hash = hashDoc(doc);
        hashDoc.put(doc, hash);
    }
    
    public String hashDoc(String doc) {
        // return DigestUtils.md5Hex(doc);
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(doc.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean checkHashDoc(String doc) {          // return true if doc is already in the hashSet
        System.out.println("hash set " + hashSet);
        String hash = hashDoc(doc);
        if (hash == null) {
            return false;
        }
        if (hashSet.contains(hash)) {
            return true;
        } else {
            hashSet.add(hash);
            return false;
        }
    }

    public Set<String> getHashDoc() {
        return hashSet;
    }

}
