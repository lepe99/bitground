import {lazy, Suspense} from "react";
import {createBrowserRouter, Outlet} from "react-router-dom";
import RootLayout from "../layout/RootLayout.jsx";
import Main from "../pages/Main.jsx";
import ProtectedRoute from "./ProtectedRoute.jsx";
import Loading from "../components/Loading.jsx";

// 첫 화면(Main)만 바로 불러오고, 나머지 페이지는 처음 방문할 때 따로 받는다.
// (Quill 에디터, Recharts, 거래소 화면 등이 첫 화면 번들에 섞이지 않도록)
const Callback = lazy(() => import("../pages/auth/Callback.jsx"));
const Login = lazy(() => import("../pages/Login.jsx"));
const CoinTrends = lazy(() => import("../components/CoinTrends.jsx"));
const Rank = lazy(() => import("../pages/Rank.jsx"));
const Mypage = lazy(() => import("../pages/Mypage.jsx"));
const NoticePage = lazy(() => import("../pages/NoticePage.jsx"));
const Trade = lazy(() => import("../pages/Trade.jsx"));
const Investments = lazy(() => import("../pages/Investments.jsx"));
const PostList = lazy(() => import("../components/community/PostList.jsx"));
const PostWrite = lazy(() => import("../components/community/PostWrite.jsx"));
const PostDetail = lazy(() => import("../components/community/PostDetail.jsx"));
const TickerProvider = lazy(() =>
    import("../ticker/TickerProvider.jsx").then(m => ({default: m.TickerProvider})));

const page = (element) => <Suspense fallback={<Loading/>}>{element}</Suspense>;

export const router = createBrowserRouter([
    {
        path: "/",
        element: <RootLayout />,
        children: [
            {index: true, element: <Main />},
            {path: "auth/callback", element: page(<Callback/>)},
            {path: "login", element: page(<Login/>)},
            {path: "trends", element: page(<CoinTrends/>)},
            {
                element: <ProtectedRoute />,
                children: [
                    // 여기에 보호된 라우트 추가
                    {path: "rank", element: page(<Rank/>)},
                    {path: "mypage", element: page(<Mypage/>)},
                    {path: "notice", element: page(<NoticePage/>)},
                    // upbit ticker
                    {
                        element: (
                            <Suspense fallback={<Loading/>}>
                                <TickerProvider><Outlet/></TickerProvider>
                            </Suspense>
                        ),
                        children: [
                            {path: "trade", element: page(<Trade/>)},
                            {path: "investments", element: page(<Investments/>)},
                        ]
                    },
                    {
                        path: "community",
                        children: [
                            {index: true, element: page(<PostList/>)},     // /community
                            {path: "write", element: page(<PostWrite/>)},  // /community/write
                            {path: ":id", element: page(<PostDetail/>)}    // /community/:id
                        ]
                    }
                ]
            }
        ]
    }
]);

export default router;
