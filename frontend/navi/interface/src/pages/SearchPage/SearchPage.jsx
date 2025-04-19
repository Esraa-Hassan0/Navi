import React, { useState, useEffect } from "react";
import svglogo from "../../assets/svglogo.svg";
import rightshape from "../../assets/rightshape.svg";
import leftshape from "../../assets/leftshape.svg";
import bottomshape from "../../assets/bottomshape.svg";
import { IoSearch } from "react-icons/io5";
import axios from "axios";
import "./SearchPage.css";
import { useNavigate } from "react-router-dom";

function SearchPage() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [snippets, setSnippets] = useState({});
  const [suggestions, setSuggestions] = useState([]); // State for suggestions

  // Fetch suggestions as the user types
  useEffect(() => {
    const fetchSuggestions = async () => {
      if (!query || query.trim() === "") {
        setSuggestions([]);
        return;
      }
      try {
        const response = await axios.get(
          "http://localhost:8080/suggestions?query=" + query,
          { query }
        );
        setSuggestions(response.data || []);
        console.log("Suggestions:", response.data);
      } catch (error) {
        console.error("Error fetching suggestions:", error);
        setSuggestions([]);
      }
    };

    // Debounce the API call to avoid excessive requests
    const debounce = setTimeout(fetchSuggestions, 300);
    return () => clearTimeout(debounce);
  }, [query]);

  const handleKeyPress = (e) => {
    if (e.key === "Enter") {
      handleSearch(e);
    }
  };

  const handleSearch = async (e, searchQuery) => {
    e.preventDefault();
    setSuggestions([]); // Clear suggestions on search
    try {
      const response = await axios.post(
        "http://localhost:8080/search?query=" + query,
        { query }
      );
      const searchResults = response.data;
      setResults(searchResults);
      console.log("Search results:", searchResults);
      console.log("Query:", query);
      navigate("/results", {
        state: { results: searchResults, query: searchQuery==null ? query : searchQuery },
      });
    } catch (error) {
      console.error("Error fetching search results:", error);
      navigate("/results", {
        state: { results: [], error: "Error fetching results." },
      });
    }
  };

  // Handle suggestion click
  const handleSuggestionClick = (suggestion) => {
    console.log("Query:", suggestion);

    setQuery(suggestion);
    setSuggestions([]); // Clear suggestions
    handleSearch(new Event("submit"), suggestion); // Trigger search with the selected suggestion
  };

  return (
    <>
      <div className="SearchPage">
        <div className="searchcontainer">
          <img src={rightshape} alt="Rightshape" className="rshape" />
          <img src={leftshape} alt="Leftshape" className="lshape" />
          <img src={bottomshape} alt="Bottomshape" className="bshape" />
        </div>
        <div className="center">
          <img src={svglogo} alt="Logo" className="logo_Search" />
          <div className="searchbar-container">
            <div className="searchbar">
              <IoSearch style={{ color: "gray", marginLeft: "10px" }} />
              <input
                placeholder="Search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyPress={handleKeyPress}
              />
            </div>
            {/* Suggestions Dropdown */}
            {suggestions.length > 0 && (
              <ul className="suggestions-dropdown">
                {suggestions.map((suggestion, index) => (
                  <li
                    key={index}
                    onClick={() => handleSuggestionClick(suggestion)}
                    className="suggestion-item"
                  >
                    <IoSearch style={{ color: "gray", marginRight: "8px" }} />
                    {suggestion}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>
    </>
  );
}

export default SearchPage;
