
- Connect to engine
- Ranking
- Pushing 

[^1]: 
-> modify constructor: done
-> call rankPhrase inside rank: done
->rankPhrase will call getMatchedDocs, then rank them?

-> How to get fields to phrase???????
- Indexer getters
- storing in db -> document or arrays of fields

->Email to TA: BM25P


Formula 
`finalScore = relevanceWeight * phraseFieldScore + popularityWeight * pageRankScore;`

for relevance 
score += weight[field] * (phraseFreq / fieldLength)



## 📦 Suggested Tweaks (Just Polishing)

### 1. **Make `fields` and `weight` constants**

Move these out of `Relevance` into `Ranker` as `static final` arrays:

java

CopyEdit

`private static final String[] FIELDS = { "h1", "h2", "a", "other" }; private static final double[] FIELD_WEIGHTS = { 2.5, 2.0, 1.5, 1.0 };`

Use them in both BM25F and phrase scoring to ensure consistency.