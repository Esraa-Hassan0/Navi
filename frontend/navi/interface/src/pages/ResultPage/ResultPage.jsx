import React, { useState } from "react";
import logo from "../../assets/logo.png";
import logoLightMode from "../../assets/logoLightMode.png";
import DarkModeIcon from "../../assets/darkmodeicon2.png";
import LightModeIcon from "../../assets/lightmodeicon.png";
import { faMagnifyingGlass } from "@fortawesome/free-solid-svg-icons"; // For search icon
import "./ResultPage.css";
function ResultPage() {
  const [isDarkMode, setDarkMode] = useState(false);

  const toggleDarkMode = () => {
    setDarkMode((prevMode) => !prevMode);
  };
  return (
    <>
      {/* <FontAwesomeIcon icon={faMagnifyingGlass} style={{ color: "#f93ee9" }} /> */}
      <div className={`mainContainer ${isDarkMode ? "dark" : "light"}`}>
        {!isDarkMode && <img src={logo} className="logo" />}
        {isDarkMode && <img src={logoLightMode} className="logoLightMode" />}
        {/* <FontAwesomeIcon
          icon="fa-solid fa-magnifying-glass"
          className="searchIcon"
        /> */}
        <input placeholder="Search" className="SearchBar"></input>
        {!isDarkMode && (
          <img
            src={DarkModeIcon}
            className="darkModeIcon"
            onClick={toggleDarkMode}
          />
        )}
        {isDarkMode && (
          <img
            src={LightModeIcon}
            className={`lightModeIcon ${isDarkMode ? "dark" : "light"}`}
            onClick={toggleDarkMode}
          />
        )}
      </div>
    </>
  );
}

export default ResultPage;
