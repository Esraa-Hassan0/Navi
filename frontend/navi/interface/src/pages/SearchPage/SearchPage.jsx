import React from "react";
import svglogo from "../../assets/svglogo.svg";
import rightshape from "../../assets/rightshape.svg";
import leftshape from "../../assets/leftshape.svg";
import bottomshape from "../../assets/bottomshape.svg"
import { IoSearch } from "react-icons/io5";
import "./SearchPage.css";

function SearchPage() {
    return (
        <>
        <div className="SearchPage">
            <div className="searchcontainer">
                <img 
                    src={rightshape}
                    alt="Rightshape"
                    className="rshape"
                />
                <img 
                    src={leftshape}
                    alt="Leftshape"
                    className="lshape"
                />
                <img
                    src={bottomshape}
                    alt="Bottomshape"
                    className="bshape"
                />
            </div>
                <div className="center">
                    <img
                        src={svglogo}
                        alt="Logo"
                        className="logo"
                    />
                    <div className="searchbar">
                        <IoSearch style={{color:"gray", marginLeft: "10px"}}/>
                        <input placeholder="Search" />
                    </div>
                </div>
        </div>
        </>
    );
}

export default SearchPage;