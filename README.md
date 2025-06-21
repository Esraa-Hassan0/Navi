
<div align="center">
<p>
  <img width="720" height ="300" align="center" src="https://i.postimg.cc/zf0XcKdQ/image.png" alt="navi"/>
</p>
<h1>
    <img src="https://i.postimg.cc/5Npk88wk/ribbon.png" alt="ribbon" width="28" /> Navi <img src="https://i.postimg.cc/5Npk88wk/ribbon.png" alt="ribbon" width="28" />
</h1>
<p>Navigate the cutest search engine ever!</p>
</div>

## <img src="https://i.postimg.cc/x8ht8PrX/question-mark-1.pnghttps://i.postimg.cc/x8ht8PrX/question-mark-1.png" width = "28" /> What is Navi?

**Navi** is a simple, modular search engine that demonstrates the core components of modern search systems. It features a **web crawler**, **indexer**, **ranking engine**, and **query processor**, all integrated through a fast and responsive web interface.


## <img src="https://i.postimg.cc/SxFj7zkC/features-1.png" width = "28" /> Features

### <img src="https://i.postimg.cc/wjKwdFcP/web-1.png" width = "24" /> Crawler

- Begins from a seed set of URLs and recursively fetches HTML pages.
    
- Parses and follows hyperlinks while respecting `robots.txt`.
    
- Supports multi-threaded crawling with configurable thread count.
    
- Avoids duplicate pages using URL normalization and compact string matching.
    
- Saves crawling progress to resume on failure.
    
- Gathers approximately **6000 HTML pages** for indexing.
    

### <img src="https://i.postimg.cc/k5mYWw28/card-index.png" width = "24" /> Indexer

- Extracts and stores terms from HTML documents, distinguishing between:
    
    - **Title**
        
    - **Headings**
        
    - **Body**
        
- Indexed data is stored persistently in **MongoDB** for efficient access.
    
- Supports incremental updates for new crawled pages.
    
- Designed for fast retrieval of matching documents and field-based term weighting.
    
- **Indexing time:** ~10 minutes for 6000 documents.
    

### <img src="https://i.postimg.cc/59RPsfHf/ranking-2.png" width = "24" /> Ranker

Combines two scoring mechanisms for robust ranking:

1. **BM25F (Fielded BM25)** – _Relevance-based scoring_
    
    - **Field Weighting:** Assigns different importance (weights) to each field
        
        - Title: `2.0`
            
        - Heading: `1.5`
            
        - Body: `1.0`
            
    - **Term Frequency Normalization:** Adjusts term frequency (TF) per field to account for varying field lengths, preventing bias toward longer fields.
		
    - **Field Length Normalization**: Uses field-specific length normalization parameters to adjust for differences in field verbosity.
        
2. **PageRank** – _Popularity-based scoring_
    
    - Computes global importance of pages based on link structure.
        
    - Used to boost commonly cited or authoritative sources.
        

The final score is a hybrid of **relevance** and **popularity**, improving both precision and trustworthiness of results.

### <img src="https://i.postimg.cc/Qt66G30k/data-searching.png" width = "24" /> Query Engine

- Supports single-word, multi-word, and **phrase searches** (with quotation marks).
    
- Applies **stemming** for better query matching (e.g., “travel” matches “traveler”).
    
- Displays:
    
    - Page title
        
    - URL
        
    - Snippet with query terms in **bold**
        
- Efficient performance:
    
    - **Search time:** ~0.5 seconds per query.
        
- Includes:
    
    - Pagination
        
    - Popular query suggestions (autocomplete)
        
    - Boolean search: `AND`, `OR`, `NOT` operators (max 2 per query)
        


## <img src="https://i.postimg.cc/XYrfz5CJ/coding-1.png" width = "26" /> Tech Stack

- **Backend:** <img src="https://i.postimg.cc/KYv2bZmR/icons8-spring-boot-48.pnghttps://i.postimg.cc/KYv2bZmR/icons8-spring-boot-48.png" width = "16" /> Spring Boot
- **Frontend:** <img src="https://i.imgur.com/ZAdKucE.png" width="14" /> React.js, <img src="https://i.imgur.com/hj45tsb.png" width="14" /> Figma
- **Database:** <img src="https://i.postimg.cc/cLcSPKyp/icons8-mongodb-24.png" width = "16" /> MongoDB


## <img src="https://i.postimg.cc/Jh56xJXF/download.png" width = "28" /> How to Run

1. **Clone the repository**
    
    `git clone https://github.com/AmiraKhalid04/Navi.git cd Navi`
    
2. **Run the Backend**
    
    `cd backend/navi ./mvnw spring-boot:run`
    
3. **Run the Frontend**
    
    `cd frontend npm install npm start`
    
4. **Start Crawling & Indexing**
    
    - Run the crawler to fetch pages.
        
    - Run the indexer to store parsed terms and metadata.
        
5. **Search**
    
    - Access the web interface at `http://localhost:3000`
        
    - Enter search queries and explore ranked results.
        


## <img src="https://i.postimg.cc/FKqjvH0Z/group-5.png" width = "28" /> Contributors

| <a href="https://avatars.githubusercontent.com/AmiraKhalid04?v=4"><img src="https://avatars.githubusercontent.com/AmiraKhalid04?v=4" alt="Amira Khalid" width="150"></a> | <a href="https://avatars.githubusercontent.com/Esraa-Hassan0?v=4"><img src="https://avatars.githubusercontent.com/Esraa-Hassan0?v=4" alt="Esraa Hassan" width="150"></a> | <a href="https://avatars.githubusercontent.com/Alyaa242?v=4"><img src="https://avatars.githubusercontent.com/Alyaa242?v=4" alt="Alyaa Ali" width="150"></a> | <a href="https://avatars.githubusercontent.com/Hagar3bdelsalam?v=4"><img src="https://avatars.githubusercontent.com/Hagar3bdelsalam?v=4" alt="Hagar Abdelsalam" width="150"></a> |
| :----------------------------------------------------------------------------------------------------------------------------------------------------------------------: | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------: | :---------------------------------------------------------------------------------------------------------------------------------------------------------: | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------: |
|                                                             [Amira Khalid](https://github.com/AmiraKhalid04)                                                             |                                                             [Esraa Hassan](https://github.com/Esraa-Hassan0)                                                             |                                                          [Alyaa Ali](https://github.com/Alyaa242)                                                           |                                                              [Hagar Abdelsalam](https://github.com/Hagar3bdelsalam)                                                              |
