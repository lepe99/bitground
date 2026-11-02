import "./Header.css"
import "../style.css"
import {MdArrowOutward} from "react-icons/md";
import {useNavigate} from "react-router-dom";
import {useAuth} from "../auth/useAuth";
import {useLocation} from "react-router-dom";
import {FaRegBell} from "react-icons/fa6";
import {useEffect, useState} from "react";
import {useToast} from "../components/Toast.jsx";
import api from "../api/axiosConfig.js";

export default function Header() {
    const navigate = useNavigate();

    const {logout, isLoggedIn} = useAuth();

    const location = useLocation();
    const path = location.pathname;
    const {unreadNotificationsCount, setUnreadNotificationsCount} = useToast();

    // 툴팁의 노출 여부를 관리하는 state를 추가 (초기값: false)
    const [isTooltipVisible, setIsTooltipVisible] = useState(false);
    const [notifications, setNotifications] = useState([]);
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [isLoading, setIsLoading] = useState(false);
    const pageSize = 10;

    //모바일사이즈
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    const toggleMenu = () => setIsMenuOpen(prev => !prev);
    const closeMenu = () => setIsMenuOpen(false);

    useEffect(() => {
        const handleClickOutside = (e) => {
            if (!e.target.closest('.notification-container') && !e.target.closest('.mobile-notification')) {
                setIsTooltipVisible(false);
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    useEffect(() => {
        if (isTooltipVisible) {
            // 알림 데이터를 가져오는 API 호출
            const fetchNotifications = async () => {
                if (isLoading) return;
                setIsLoading(true);
                try {
                    const response = await api.get('/notifications', {
                        params: {
                            page: currentPage, size: pageSize, sort: 'createdAt,desc',
                        },
                    });
                    const data = response.data.content || response.data;
                    const total = response.data.totalPages || 1;

                    // 첫 페이지면 알림 목록을 초기화하고, 아니면 이전 알림에 새로운 알림을 추가
                    if (currentPage === 0) {
                        setNotifications(data);
                    } else {
                        setNotifications(prev => [...prev, ...data]);
                    }

                    setTotalPages(total);
                } catch (error) {
                    console.error('알림 데이터를 가져오는 중 오류 발생:', error);
                } finally {
                    setIsLoading(false);
                }
            };
            fetchNotifications();
        }
    }, [isTooltipVisible, currentPage]);

    // 페이지 이동 함수
    const handlePageChange = (newPage) => {
        if (newPage >= 0 && newPage < totalPages) {
            setCurrentPage(newPage);
        }
    };

    // 스크롤 이벤트 처리 함수
    const handleScroll = (e) => {
        const {scrollTop, scrollHeight, clientHeight} = e.target;
        // 스크롤이 90% 이상 내려갔을 때 다음 페이지로 이동
        if ((scrollTop + clientHeight) / scrollHeight > 0.9 && currentPage < totalPages - 1) {
            handlePageChange(currentPage + 1);
        }
    };

    // 알림 클릭 시 해당 페이지로 이동
    const handleNavigate = (messageType) => () => {
        switch (messageType) {
            case 'NOTICE':
                navigate('/service');
                break;
            case 'INQUIRY_UPDATE':
                navigate('/service');
                break;
            case 'ORDER_EXECUTION':
                navigate('/investments');
                break;
            case 'SEASON_UPDATE':
                navigate('/rank');
                break;
            default:
                console.warn('알 수 없는 알림 타입:', messageType);
        }
        setIsTooltipVisible(false); // 툴팁 닫기
    };



    useEffect(() => {
        if (isTooltipVisible) {
            // 안읽은 알림을 읽음처리하는 API 호출
            const markNotificationsAsRead = async () => {
                try {
                    await api.patch('/notifications');
                    setUnreadNotificationsCount(0); // 알림 개수를 0으로 초기화
                } catch (error) {
                    console.error('알림 읽음 처리 중 오류 발생:', error);
                }
            };
            markNotificationsAsRead();
        }
    }, [isTooltipVisible, setUnreadNotificationsCount]);


    return (<header className={"header"}>
            <div className="header-container">
                <div className="logo" onClick={() => navigate("/")}>
                    *Bitground
                </div>

                <div className={"nav-container"}>
                    <div className={"nav-frame"}>
                        <div className={"nav-list"}>
                            <div className={`nav-sell ${path === "/trade" ? "active" : ""}`}
                                 onClick={() => navigate("/trade")}>
                                거래소
                            </div>
                            <div className={`nav-selllist ${path === "/investments" ? "active" : ""}`}
                                 onClick={() => navigate("/investments")}>
                                투자내역
                            </div>
                            <div className={`nav-news ${path === "/trends" ? "active" : ""}`}
                                 onClick={() => navigate("/trends")}>
                                최신동향
                            </div>
                            <div
                                className={`nav-community ${path.startsWith("/community") ? "active" : ""}`}
                                onClick={() => navigate("/community")}>
                                커뮤니티
                            </div>
                            <div className={`nav-rank ${path === "/rank" ? "active" : ""}`}
                                 onClick={() => navigate("/rank")}>
                                랭킹
                            </div>
                            <div className={`nav-service ${path === "/service" ? "active" : ""}`}
                                 onClick={() => navigate("/service")}>
                                고객센터
                            </div>
                        </div>
                    </div>
                </div>
                <div className="mobile-controls">
                    <div
                        className="notification-container"
                        onMouseEnter={() => setIsTooltipVisible(true)}
                        onMouseLeave={() => setIsTooltipVisible(false)}
                    >
                        <div className="notification-wrapper">
                            <FaRegBell className='notification-btn' />
                        </div>
                        {isTooltipVisible && (
                            <div
                                className="notification-tooltip"
                                onMouseEnter={() => setIsTooltipVisible(true)}
                                onMouseLeave={() => setIsTooltipVisible(false)}
                            >
                                <div className="tooltip-header">
                                    <strong>알림 내역</strong>
                                </div>
                                <ul className="tooltip-list" onScroll={handleScroll}>
                                    {notifications.map((item, index) => (
                                        <li
                                            key={item.id}
                                            onClick={(e) => {
                                                e.stopPropagation(); // 클릭 막힘 방지
                                                handleNavigate(item.messageType)(); // 이게 핵심
                                            }}
                                        >
                                            <pre>{item.message}</pre>
                                            <span className="notification-time">
        {new Date(item.createdAt).toLocaleString()}
      </span>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        )}
                    </div>
                    <div className="mobile-menu-toggle" onClick={toggleMenu}>
                        ☰
                    </div>
                </div>
                {isMenuOpen && (
                    <div className="mobile-menu">
                        <button className="close-btn" onClick={closeMenu}>✕</button>
                        <ul className="mobile-nav-list">
                            <li onClick={() => {navigate('/trade'); closeMenu();}}>거래소</li>
                            <li onClick={() => {navigate('/investments'); closeMenu();}}>투자내역</li>
                            <li onClick={() => {navigate('/trends'); closeMenu();}}>최신동향</li>
                            <li onClick={() => {navigate('/community'); closeMenu();}}>커뮤니티</li>
                            <li onClick={() => {navigate('/rank'); closeMenu();}}>랭킹</li>
                            <li onClick={() => {navigate('/service'); closeMenu();}}>고객센터</li>
                            {isLoggedIn ? (
                                <>
                                    <li onClick={() => {navigate('/mypage'); closeMenu();}}>마이페이지</li>
                                    <li onClick={() => {logout(); closeMenu();}}>로그아웃</li>
                                </>
                            ) : (
                                <li onClick={() => {navigate('/login'); closeMenu();}}>로그인</li>
                            )}
                        </ul>
                    </div>
                )}
                <div className="header-btns">
                    {isLoggedIn ? (<>
                            <div className="mypage-group">
                                <div
                                    className="notification-container"
                                    onMouseEnter={() => setIsTooltipVisible(true)}
                                    onMouseLeave={() => setIsTooltipVisible(false)}
                                >
                                    <div className="notification-wrapper">
                                        <FaRegBell className='notification-btn'/>
                                        {unreadNotificationsCount > 0 && (<span className="notification-badge">
                                                {unreadNotificationsCount > 99 ? '99+' : unreadNotificationsCount}
                                            </span>)}
                                    </div>
                                    {isTooltipVisible && (<div className="notification-tooltip">
                                            <div className="tooltip-header">
                                                <strong>알림 내역</strong>
                                            </div>
                                            <ul className="tooltip-list"
                                                onScroll={handleScroll}>
                                                {notifications.length > 0 ? (notifications.map((item, index) => (
                                                        <li key={`notification-${item.id}-${index}`}
                                                        onClick={handleNavigate(item.messageType)}>
                                                         <pre>
                                                            {item.message}
                                                         </pre>
                                                            {item.createdAt && (<span className="notification-time">
                                                                 {new Date(item.createdAt).toLocaleString()}
                                                             </span>)}
                                                        </li>))) : (
                                                    <li className="no-notifications">새로운 알림이 없습니다.</li>)}
                                            </ul>
                                        </div>)}
                                </div>
                                <div className={`mypage-btn ${path === "/mypage" ? "active" : ""}`}
                                     onClick={() => navigate("/mypage")}>
                                    마이페이지
                                    <MdArrowOutward className="my-move-icon"/>
                                </div>
                            </div>
                            <button
                                type="button"
                                className="logout-btn"
                                onClick={logout}
                            >
                                로그아웃
                            </button>
                        </>) : (<button
                            type="button"
                            className="login-btn"
                            onClick={() => navigate("/login")}
                        >
                            로그인 <MdArrowOutward className="move-icon"/>
                        </button>)}
                </div>
            </div>
        </header>);
}