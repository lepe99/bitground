import Header from "./Header.jsx";
import {Outlet} from "react-router-dom";

export default function RootLayout() {
    return (
        <div>
            <Header/>
            <main>
                <Outlet/>
            </main>
        </div>
    );
} 