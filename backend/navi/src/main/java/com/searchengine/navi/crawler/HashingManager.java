// hash docs to check for duplicates
package com.searchengine.navi.crawler;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

// import org.apache.commons.codec.digest.DigestUtils;

public class HashingManager {
    private final Set<String> hashSet = ConcurrentHashMap.newKeySet();
    private int duplicateCount = 0;

    public HashingManager() {

    }

    public String hashDoc(String doc) {
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
        String hash = hashDoc(doc);
        if (hash == null) {
            return false;
        }
        if (hashSet.contains(hash)) {
            duplicateCount++;
            return true;
        }
        return false;
    }

    public void addHashDoc(String doc) {               // add the hash to the hashSet
        String hash = hashDoc(doc);
        if (hash != null) {
            hashSet.add(hash);
        }
    }

    public Set<String> getHashDoc() {
        return new HashSet<>(hashSet);
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }
}
