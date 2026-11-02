import {RouterProvider} from "react-router-dom";
import {AuthProvider} from "./auth/AuthProvider.jsx";
import router from "./routes/router.jsx";
import {ToastProvider} from "./components/Toast.jsx";
import {NotificationProvider} from "./notifications/NotificationProvider.jsx";


export default function App() {
    return (
        <AuthProvider>
            <ToastProvider>
                <NotificationProvider>
                    <RouterProvider router={router}/>
                </NotificationProvider>
            </ToastProvider>
        </AuthProvider>
    );
}

