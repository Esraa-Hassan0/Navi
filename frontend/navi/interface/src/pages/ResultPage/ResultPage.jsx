import React, { useState, useEffect } from "react";
import axios from "axios";
import { useLocation } from "react-router-dom";
import logo from "../../assets/logo.png";
import logoLightMode from "../../assets/logoLightMode.png";
import DarkModeIcon from "../../assets/darkmodeicon2.png";
import LightModeIcon from "../../assets/lightmodeicon.png";
import footer from "../../assets/footer2.png";
import footerdarkmode from "../../assets/footerdarkmode.png";
import shape from "../../assets/shape.png";
import shape2 from "../../assets/shape2.png";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faMagnifyingGlass,
  faSpinner,
  faArrowLeft,
  faArrowRight,
} from "@fortawesome/free-solid-svg-icons";
import "./ResultPage.css";

function ResultPage() {
  const location = useLocation();
  const [suggestions, setSuggestions] = useState([]);
  const [isDarkMode, setDarkMode] = useState(false);
  const [query, setQuery] = useState(location.state?.query || "");
  const [pendingSuggestion, setPendingSuggestion] = useState(null);
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isTyping, setIsTyping] = useState(false);
  const [tokens, setTokens] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [searchTime, setSearchTime] = useState(0);
  const [resultCount, setResultCount] = useState(0);
  const itemsPerPage = 20;

  // Call handleSearch on mount if query exists
  useEffect(() => {
    if (query && query.trim() !== "" && results.length === 0) {
      console.log("Initial query:", query);
      handleSearch({ preventDefault: () => {} });
    }
  }, []);

  // Fetch suggestions as the user types
  useEffect(() => {
    const fetchSuggestions = async () => {
      if (!query || query.trim() === "") {
        setSuggestions([]);
        return;
      }
      try {
        const response = await axios.get(
          `http://localhost:8080/suggestions?query=${encodeURIComponent(query)}`
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

  // Handle query state change for suggestion click
  useEffect(() => {
    if (pendingSuggestion && pendingSuggestion === query) {
      // Query state has updated, proceed with sequence
      setSuggestions([]);
      setIsTyping(false);
      handleSearch({ preventDefault: () => {} });
      setPendingSuggestion(null); // Clear pending suggestion
    }
  }, [query, pendingSuggestion]);

  const handleSearch = async (e) => {
    e.preventDefault();
    setLoading(true);
    setSuggestions([]); // Clear suggestions on search

    if (!query || query.trim() === "") {
      setLoading(false);
      return;
    }

    try {
      // Step 1: Get search tokens
      const searchResponse = await axios.post(
        `http://localhost:8080/search?query=${encodeURIComponent(query)}`,
        { query }
      );

      const searchTokens = searchResponse.data || [];
      setTokens(searchTokens);
      console.log("Search tokens:", searchTokens);

      // Step 2: Get search results with the same query
      const resultsResponse = await axios.get(
        `http://localhost:8080/results?query=${encodeURIComponent(query)}`
      );

      const responseData = resultsResponse.data || {};
      console.log("Results data:", responseData);

      setResults(responseData.results || []);
      setSearchTime(responseData.total_time || 0);
      setResultCount(responseData.results ? responseData.results.length : 0);
    } catch (error) {
      console.error("Error fetching search results:", error);
      setResults([]);
      setTokens([]);
    } finally {
      setLoading(false);
    }
  };

  // Handle suggestion click
  const handleSuggestionClick = (suggestion) => {
    console.log("Clicked suggestion:", suggestion);
    setQuery(suggestion); // Update search bar
    setPendingSuggestion(suggestion); // Trigger useEffect when query updates
  };

  const toggleDarkMode = () => {
    setDarkMode((prevMode) => !prevMode);
  };

  const getFaviconUrl = (url) => {
    try {
      const domain = new URL(url).hostname;
      return `https://www.google.com/s2/favicons?domain=${domain}&sz=32`;
    } catch (error) {
      console.error("Invalid URL:", url, error);
      return "https://via.placeholder.com/32";
    }
  };

  const getWebName = (url) => {
    try {
      const domain = new URL(url).hostname;
      const name = domain
        .replace(/^www\./i, "")
        .replace(/\..*$/, "")
        .replace(/^./, (str) => str.toUpperCase());
      return name;
    } catch (error) {
      console.error("Invalid URL:", url, error);
      return "Unknown";
    }
  };

  const totalPages = Math.ceil(results.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const endIndex = startIndex + itemsPerPage;
  const currentResults = results.slice(startIndex, endIndex);

  const goToPreviousPage = () => {
    if (currentPage > 1) setCurrentPage(currentPage - 1);
  };

  const goToNextPage = () => {
    if (currentPage < totalPages) setCurrentPage(currentPage + 1);
  };

  return (
    <>
      {loading ? (
        <div className="loadingContainer">
          <div className="loader"></div>
        </div>
      ) : (
        <div className={`pageContainer ${isDarkMode ? "dark" : "light"}`}>
          <div className="navbar">
            {!isDarkMode && <img src={logo} className="logo" alt="Logo" />}
            {isDarkMode && (
              <img
                src={logoLightMode}
                className="logoLightMode"
                alt="Logo Light Mode"
              />
            )}
            <div className="searchContainer">
              <FontAwesomeIcon
                icon={faMagnifyingGlass}
                style={{ color: "#f93ee9" }}
                className="searchIcon"
              />
              <input
                placeholder="Search"
                className="SearchBar"
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setIsTyping(true);
                  setPendingSuggestion(null); // Clear pending suggestion on manual input
                }}
                onKeyPress={handleKeyPress}
              />
              {loading && (
                <FontAwesomeIcon
                  icon={faSpinner}
                  spin
                  className="spinnerIcon"
                />
              )}
              {/* Suggestions Dropdown */}
              {suggestions.length > 0 && isTyping && (
                <ul className="suggestions-dropdown">
                  {suggestions.map((suggestion, index) => (
                    <li
                      key={index}
                      onClick={() => handleSuggestionClick(suggestion)}
                      className="suggestion-item"
                    >
                      <FontAwesomeIcon
                        icon={faMagnifyingGlass}
                        style={{ color: "gray", marginRight: "8px" }}
                      />
                      {suggestion}
                    </li>
                  ))}
                </ul>
              )}
            </div>
            {!isDarkMode && (
              <img
                src={DarkModeIcon}
                className="darkModeIcon"
                onClick={toggleDarkMode}
                alt="Dark Mode Toggle"
              />
            )}
            {isDarkMode && (
              <img
                src={LightModeIcon}
                className="lightModeIcon"
                onClick={toggleDarkMode}
                alt="Light Mode Toggle"
              />
            )}
          </div>
          <img src={shape} className="shapeImage" alt="Shape Decoration" />
          <img src={shape2} className="shapeImage2" alt="Shape 2 Decoration" />
          {results.length > 0 && (
            <>
              <div className="data">
                <div className="timeSearching">
                  You found {resultCount} items related in {searchTime / 1000} s
                </div>
              </div>
              <div className="Tokens">
                <ul className="listTokens">
                  {tokens.map((token, index) => (
                    <li key={index} className="token">
                      {token}
                    </li>
                  ))}
                </ul>
              </div>
              <div className="contentWrapper">
                <ul className="resultsList">
                  {currentResults.map((result, index) => (
                    <li key={index} className="resultItem">
                      <div className="resultWithFavicon">
                        <div className="firstBlock">
                          <div className="firstBlock1">
                            <img
                              src={getFaviconUrl(result.url)}
                              alt={`${result.title} favicon`}
                              className="resultFavicon"
                            />
                          </div>
                          <div className="firstBlock2">
                            <div className="webName">
                              {getWebName(result.url)}
                            </div>
                            <a
                              href={result.url}
                              className="resultUrl"
                              target="_blank"
                              rel="noopener noreferrer"
                            >
                              {result.url}
                            </a>
                          </div>
                        </div>
                        <div className="resultContent">
                          <h3 className="resultTitle">
                            <a
                              href={result.url}
                              target="_blank"
                              rel="noopener noreferrer"
                            >
                              {result.title}
                            </a>
                          </h3>
                          <p className="resultSnippet">
                            <div
                              dangerouslySetInnerHTML={{
                                __html: result.snippets,
                              }}
                            />
                          </p>
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>
                <div className="suggestions">
                  <h4>Related Searches</h4>
                  <ul className="suggestionList">
                    {suggestions.length > 0 ? (
                      suggestions.map((suggestion, index) => (
                        <li
                          key={index}
                          className="suggestionItem"
                          onClick={() => handleSuggestionClick(suggestion)}
                        >
                          {suggestion}
                        </li>
                      ))
                    ) : (
                      <li className="noSuggestions">No related searches</li>
                    )}
                  </ul>
                </div>
              </div>
              {results.length > itemsPerPage && (
                <div className="pagination">
                  <button
                    onClick={goToPreviousPage}
                    disabled={currentPage === 1}
                    className="pageButton"
                  >
                    <FontAwesomeIcon icon={faArrowLeft} />
                  </button>
                  <span className="pageInfo">
                    Page {currentPage} of {totalPages}
                  </span>
                  <button
                    onClick={goToNextPage}
                    disabled={currentPage === totalPages}
                    className="pageButton"
                  >
                    <FontAwesomeIcon icon={faArrowRight} />
                  </button>
                </div>
              )}
            </>
          )}
          {results.length === 0 && <div style={{ flexGrow: 1 }}></div>}
          {!isDarkMode && (
            <img
              src={footer}
              alt="Footer Image"
              className={`footer ${results.length > 0 ? "items" : "no_items"}`}
            />
          )}
          {isDarkMode && (
            <div
              className={`footerdark ${
                results.length > 0 ? "items" : "no_items"
              }`}
            ></div>
          )}
        </div>
      )}
    </>
  );
}

export default ResultPage;
