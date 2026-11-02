import React, { useState } from "react";
import { useAuth } from "../auth/useAuth.js";
import Notice from "../components/notice/Notice.jsx";
import NoticeWrite from "../components/notice/NoticeWrite.jsx";
import "../styles/notice/notice.css";

export default function NoticePage() {
    const [isWriting, setIsWriting] = useState(false);
    const { user } = useAuth();
    const isAdmin = user?.role === 'ROLE_ADMIN';
    const [keyword, setKeyword] = useState('');

    return (
        <div className="service">
            <div className="service-container">
                <div className="service-title">
                    <span>* 공지사항</span>
                    {!isWriting && (
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
                        <div className={`notice ${!isWriting ? 'active' : ''}`}
                             onClick={() => {
                                 setIsWriting(false);
                                 setKeyword('');
                             }}>
                            공지사항
                        </div>

                        {isAdmin && (
                            <button className="askbtn" onClick={() => setIsWriting(true)}>공지등록</button>
                        )}
                    </div>

                    <div className="content-list">
                        {isWriting
                            ? <NoticeWrite onDone={() => setIsWriting(false)} />
                            : <Notice keyword={keyword} />}
                    </div>
                </div>
            </div>
        </div>
    );
}
