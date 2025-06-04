# Navi

Navigate the cutest search engine ever! 🎀

## 🚀 What is Navi?

**Navi** is a simple, modular search engine that demonstrates the core components of modern search systems. It features a **web crawler**, **indexer**, **ranking engine**, and **query processor**, all integrated through a fast and responsive web interface.

---

## 🚀 Features

### 🕷️ Crawler

- Begins from a seed set of URLs and recursively fetches HTML pages.
    
- Parses and follows hyperlinks while respecting `robots.txt`.
    
- Supports multi-threaded crawling with configurable thread count.
    
- Avoids duplicate pages using URL normalization and compact string matching.
    
- Saves crawling progress to resume on failure.
    
- Gathers approximately **6000 HTML pages** for indexing.
    

### 🗂️ Indexer

- Extracts and stores terms from HTML documents, distinguishing between:
    
    - **Title**
        
    - **Headings**
        
    - **Body**
        
- Indexed data is stored persistently in **MongoDB** for efficient access.
    
- Supports incremental updates for new crawled pages.
    
- Designed for fast retrieval of matching documents and field-based term weighting.
    
- **Indexing time:** ~10 minutes for 6000 documents.
    

### ⚖️ Ranker

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

### 🔎 Query Engine

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
        

---

## 🧱 Tech Stack

| Layer    | Technology            |
| -------- | --------------------- |
| Backend  | **Java**, Spring Boot |
| Frontend | **React.js**          |
| Database | **MongoDB**           |

---

## 📦 How to Run

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
        

---

## Contributors

| <a href="https://avatars.githubusercontent.com/AmiraKhalid04?v=4"><img src="https://avatars.githubusercontent.com/AmiraKhalid04?v=4" alt="Amira Khalid" width="150"></a> | <a href="https://avatars.githubusercontent.com/Esraa-Hassan0?v=4"><img src="https://avatars.githubusercontent.com/Esraa-Hassan0?v=4" alt="Esraa Hassan" width="150"></a> | <a href="https://avatars.githubusercontent.com/Alyaa242?v=4"><img src="https://avatars.githubusercontent.com/Alyaa242?v=4" alt="Alyaa Ali" width="150"></a> | <a href="https://avatars.githubusercontent.com/Hagar3bdelsalam?v=4"><img src="https://avatars.githubusercontent.com/Hagar3bdelsalam?v=4" alt="Hagar Abdelsalam" width="150"></a> |
| :----------------------------------------------------------------------------------------------------------------------------------------------------------------------: | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------: | :---------------------------------------------------------------------------------------------------------------------------------------------------------: | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------: |
|                                                             [Amira Khalid](https://github.com/AmiraKhalid04)                                                             |                                                             [Esraa Hassan](https://github.com/Esraa-Hassan0)                                                             |                                                          [Alyaa Ali](https://github.com/Alyaa242)                                                           |                                                              [Hagar Abdelsalam](https://github.com/Hagar3bdelsalam)                                                              |
