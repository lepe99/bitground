import {RouterProvider} from "react-router-dom";
import {AuthProvider} from "./auth/AuthProvider.jsx";
import router from "./routes/router.jsx";
import {ToastProvider} from "./components/Toast.jsx";


export default function App() {
    return (
        <AuthProvider>
            <ToastProvider>
                <RouterProvider router={router}/>
            </ToastProvider>
        </AuthProvider>
    );
}

