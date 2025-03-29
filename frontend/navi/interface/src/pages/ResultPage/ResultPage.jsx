import React, { useState } from "react";
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

// Dummy data with more than 20 items for testing pagination
const dummyResults = Array.from({ length: 25 }, (_, i) => ({
  url: `https://example.com/sample${i + 1}`,
  title: `Sample Document ${i + 1}`,
  snippet: `This is sample document ${i + 1} for testing search functionality.`,
}));

// Dummy suggestions
const dummySuggestions = [
  "Sample Search Tips",
  "Sample Documents Guide",
  "Testing Search Features",
  "Search Engine Basics",
];

function ResultPage() {
  const [isDarkMode, setDarkMode] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [tokens, setTokens] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 20;

  const toggleDarkMode = () => {
    setDarkMode((prevMode) => !prevMode);
  };

  const handleSearch = () => {
    if (!query.trim()) return;
    setLoading(true);
    setTimeout(() => {
      const filteredResults = dummyResults.filter(
        (result) =>
          result.title.toLowerCase().includes(query.toLowerCase()) ||
          result.snippet.toLowerCase().includes(query.toLowerCase())
      );
      setResults(filteredResults.length > 0 ? filteredResults : dummyResults);
      setCurrentPage(1);
      setLoading(false);
    }, 1000);
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

  const handleKeyPress = (e) => {
    if (e.key === "Enter") {
      handleSearch();
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

  const handleSuggestionClick = (suggestion) => {
    setQuery(suggestion);
    handleSearch();
  };

  return (
    <>
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
              onChange={(e) => setQuery(e.target.value)}
              onKeyPress={handleKeyPress}
            />
            {loading && (
              <FontAwesomeIcon icon={faSpinner} spin className="spinnerIcon" />
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
                You found {results.length} items related in 3.1111 s
              </div>
            </div>
            <div className="Tokens">
              <ul className="listTokens">
                <li className="token">token</li>
                <li className="token">Search</li>
                <li className="token">engine</li>
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
                        <h3 className="resultTitle">{result.title}</h3>
                        <p className="resultSnippet">{result.snippet}</p>
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
              <div className="suggestions">
                <h4>Related Searches</h4>
                <ul className="suggestionList">
                  {dummySuggestions.map((suggestion, index) => (
                    <li
                      key={index}
                      className="suggestionItem"
                      onClick={() => handleSuggestionClick(suggestion)}
                    >
                      {suggestion}
                    </li>
                  ))}
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
        {!isDarkMode && (
          <img
            src={footer}
            alt="Footer Image"
            className={`footer ${results.length > 0 ? "items" : "no_items"}`}
          />
        )}
        {isDarkMode && (
          <div
            className={`footerdark ${results.length > 0 ? "items" : "no_items"}`}
          ></div>
        )}
      </div>
    </>
  );
}

export default ResultPage;
