import React, { useState } from "react";
import logo from "../../assets/logo.png";
import logoLightMode from "../../assets/logoLightMode.png";
import DarkModeIcon from "../../assets/darkmodeicon2.png";
import LightModeIcon from "../../assets/lightmodeicon.png";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faMagnifyingGlass,
  faSpinner,
} from "@fortawesome/free-solid-svg-icons";

import "./ResultPage.css";

// Dummy data with url, title, snippet
const dummyResults = [
  {
    url: "https://example.com/sample1",
    title: "Sample Document 1",
    snippet: "This is a sample document for testing search functionality.",
  },
  {
    url: "https://example.com/html-files",
    title: "HTML Files Tutorial",
    snippet: "Learn how to work with HTML files in this tutorial.",
  },
  {
    url: "https://example.com/testing",
    title: "Testing Page Result",
    snippet: "A page dedicated to testing search engine results.",
  },
  {
    url: "https://example.com/search-basics",
    title: "Search Engine Basics",
    snippet: "Understand the basics of building a search engine.",
  },
  {
    url: "https://example.com/dummy5",
    title: "Dummy Result 5",
    snippet: "A placeholder result for demonstration purposes.",
  },
];

function ResultPage() {
  const [isDarkMode, setDarkMode] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);

  const toggleDarkMode = () => {
    setDarkMode((prevMode) => !prevMode);
  };

  const handleSearch = () => {
    if (!query.trim()) return;
    setLoading(true);
    // Simulate backend fetch with dummy data
    setTimeout(() => {
      const filteredResults = dummyResults.filter(
        (result) =>
          result.title.toLowerCase().includes(query.toLowerCase()) ||
          result.snippet.toLowerCase().includes(query.toLowerCase())
      );
      setResults(filteredResults.length > 0 ? filteredResults : dummyResults);
      setLoading(false);
    }, 1000); // Simulate 1-second delay
  };

  const handleKeyPress = (e) => {
    if (e.key === "Enter") {
      handleSearch();
    }
  };

  return (
    <>
      <div className={`pageContainer ${isDarkMode ? "dark" : "light"}`}>
        <div className="mainContainer">
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
        {results.length > 0 && (
          <ul className="resultsList">
            {results.map((result, index) => (
              <li key={index} className="resultItem">
                <a
                  href={result.url}
                  className="resultUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {result.url}
                </a>
                <h3 className="resultTitle">{result.title}</h3>
                <p className="resultSnippet">{result.snippet}</p>
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

export default ResultPage;
