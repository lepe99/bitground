import React, { useState, useEffect } from "react";
import { useAuth } from "../auth/useAuth.js";
import Notice from "../components/service/Notice.jsx";
import Ask from "../components/service/Ask.jsx";
import AskWrite from "../components/service/AskWrite.jsx";
import NoticeWrite from "../components/service/NoticeWrite.jsx";
import "../styles/service/service.css";

export default function Service() {
    const [selectedMenu, setSelectedMenu] = useState('notice');
    const { user } = useAuth();
    const isAdmin = user?.role === 'ROLE_ADMIN';
    const [keyword, setKeyword] = useState('');

    useEffect(() => {
        const saved = localStorage.getItem('serviceMenu');
        if (saved === 'ask') {
            setSelectedMenu('ask');
            localStorage.removeItem('serviceMenu');
        }
    }, []);

    return (
        <div className="service">
            <div className="service-container">
                <div className="service-title">
                    <span>
                    * 고객센터</span>
                    {['notice', 'ask'].includes(selectedMenu) && (
                        <input className={"service-search-input"}
                            type="text"
                            value={keyword}
                            onChange={(e) => setKeyword(e.target.value)}
                            placeholder="검색어를 입력하세요"
                        />
                    )}
                </div>

                <div className="service-content">
                    <div className="service-list">
                        <div className={`notice ${selectedMenu === 'notice' ? 'active' : ''}`}
                             onClick={() => {
                                 setSelectedMenu('notice');
                                 setKeyword('');
                             }}>
                            공지사항
                        </div>

                        <div className={`ask ${selectedMenu === 'ask' ? 'active' : ''}`}
                             onClick={() => {
                                 setSelectedMenu('ask');
                                 setKeyword('');
                             }}>
                            1:1 문의사항
                        </div>

                        {!isAdmin ? (
                            <button className="askbtn" onClick={() => setSelectedMenu('askwrite')}>문의하기</button>
                        ) : (
                            <button className="askbtn" onClick={() => setSelectedMenu('noticewrite')}>공지등록</button>
                        )}
                    </div>

                    <div className="content-list">
                        {selectedMenu === 'notice' && <Notice keyword={keyword} />}
                        {selectedMenu === 'ask' && <Ask keyword={keyword} />}
                        {selectedMenu === 'askwrite' && <AskWrite setSelectedMenu={setSelectedMenu} />}
                        {selectedMenu === 'noticewrite' && <NoticeWrite setSelectedMenu={setSelectedMenu} />}
                    </div>
                </div>
            </div>
        </div>
    );
}