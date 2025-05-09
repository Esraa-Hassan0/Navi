// package com.searchengine.navi.crawler;

// import java.net.URISyntaxException;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.ArrayList;


// public class UrlGraph {
//     private Map<Url, List<Url>> graph;
//     private Map<Url, List<Url>> reverseGraph;

//     public UrlGraph() {
//         graph = new HashMap<Url, List<Url>>();
//         reverseGraph = new HashMap<Url, List<Url>>();
//     }

//     public List<Url> getParents(Url url) {
//         if (reverseGraph.containsKey(url)) {
//             return reverseGraph.get(url);
//         }
//         return null;
//     }

//     public void addEdgeUrl(Url source, Url destination) throws URISyntaxException {
//         // handle urls to know the same urls ==> help in ranking
//         UrlNomalizer urlNomalizer = new UrlNomalizer();
//         source.setUrl(urlNomalizer.normalizeUrl(source.getUrl()));
//         destination.setUrl(urlNomalizer.normalizeUrl(destination.getUrl()));

//         if (!graph.containsKey(source)) {
//             // if absent to avoid overwrite
//             graph.putIfAbsent(source, new ArrayList<Url>());
//         }

//         graph.get(source).add(destination);
//         source.incrementUrlsOut();
//         destination.incrementUrlsIn();

//         addReverseEdgeUrl(destination, source);
//     }

//     public void addReverseEdgeUrl(Url source, Url destination) {
//         // this will make list of parents for each url
//         if (!reverseGraph.containsKey(source)) {
//             reverseGraph.putIfAbsent(source, new ArrayList<Url>());
//         }

//         reverseGraph.get(source).add(destination);
//     }

//     // iterate over all elements of the graph and get the parents list from the reversed graph to calculate the rank of this child
//     public void traverseToCalculateRank() {       
//         for (Url url : graph.keySet()) {
//             List<Url> children = graph.get(url);
//             if (children != null) {
//                 for (Url child : children) 
//                 {
//                     List<Url> parents = getParents(child);
//                     if (parents != null) {
//                         child.calcRank(parents);
//                         System.out.println("Child: " + child.getUrl() + " Rank: " + child.getRank());
//                     }
//                 }
//             }
//         }
//     }

// }
