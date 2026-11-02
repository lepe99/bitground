import React, {useEffect, useState} from 'react';
import api from '../../api/axiosConfig';
import {RiDeleteBinLine} from "react-icons/ri";
import {LuPencilLine} from "react-icons/lu";
import "../../styles/service/service.css";
import {useAuth} from '../../auth/useAuth.js';

const Ask = ({keyword}) => {
    const [inquiries, setInquiries] = useState([]);
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [expandedIds, setExpandedIds] = useState([]);
    const [answerExpandedIds, setAnswerExpandedIds] = useState([]);
    const { user } = useAuth();
    const [replyFormsVisible, setReplyFormsVisible] = useState([]);
    const [replyContent, setReplyContent] = useState({});
    const isAdmin = user?.role === 'ROLE_ADMIN';


    const fetchInquiries = async (pageNumber = 0) => {
        try {
            const res = await api.get(`/inquiries`, {
                params: {
                    page: pageNumber,
                    size: 10,
                    keyword: keyword || ''
                },
            });
            setInquiries(res.data.content);
            setTotalPages(res.data.totalPages);
            setCurrentPage(res.data.number);
        } catch (error) {
            console.error('문의사항 불러오기 실패:', error);
        }
    };

    const toggleExpand = (id, writerId) => {
        if (user.id !== writerId && !isAdmin) {
            alert("접근 권한이 없습니다.");
            return;
        }

        setExpandedIds((prev) =>
            prev.includes(id) ? prev.filter(item => item !== id) : [...prev, id]
        );
    };

    useEffect(() => {
        fetchInquiries(currentPage);
    }, [currentPage, keyword]);

    const toggleReplyForm = (id) => {
        setReplyFormsVisible((prev) => {
            return prev.includes(id)
                ? prev.filter((item) => item !== id)
                : [...prev, id];
        });
    };

    const handleReplySubmit = async (e, id) => {
        e.preventDefault();
        const content = replyContent[id];

        try {
            await api.put(`/inquiries/${id}/answer`, { content });
            alert('답변이 등록되었습니다');
            setReplyFormsVisible((prev) => prev.filter(i => i !== id));
            setAnswerExpandedIds((prev) => [...prev, id]);
            setExpandedIds((prev) => [...new Set([...prev, id])]);

            fetchInquiries(currentPage); // 답변 반영
        } catch (err) {
            console.error('답변 등록 실패', err);
        }
    };

    const toggleAnswerExpand = (id) => {
        setAnswerExpandedIds(prev =>
            prev.includes(id)
                ? prev.filter(item => item !== id)
                : [...prev, id]
        );
    };

    const handleDelete = async (id) => {
        if (window.confirm("정말 삭제하시겠습니까?")) {
            try {
                await api.delete(`/inquiries/${id}`);
                alert("삭제되었습니다.");
                setInquiries(prev => prev.filter(inquiry => inquiry.id !== id));
            } catch (err) {
                alert("삭제 실패: " + err.message);
            }
        }
    };
    return (
        <div className='ask-list'>
            <table className='ask-table'>
                <colgroup>
                    <col style={{ width: '5%' }} />
                    <col style={{ width: '60%' }} />
                    <col style={{ width: '10%' }} />
                    <col style={{ width: '15%' }} />
                    <col style={{ width: '10%' }} />
                </colgroup>
                <thead>
                <tr className='ask-table-head'>
                    <th>번호</th>
                    <th>제목</th>
                    <th></th>
                    <th>작성자</th>
                    <th>등록일</th>
                </tr>
                </thead>
                <tbody>
                {inquiries.map((q) => (
                    <React.Fragment key={q.id}>
                        <tr className='ask-row'>
                            <td>{q.id}</td>
                            <td className='ask-title' onClick={() => toggleExpand(q.id, q.writerId)}>
                                {q.title}
                            </td>
                            <td>
                                {isAdmin && (
                                    <LuPencilLine onClick={() => toggleReplyForm(q.id)} className='writeicon' />
                                )}
                                {(q.writerId === user.id || isAdmin) && (
                                    <RiDeleteBinLine className='delicon' onClick={() => handleDelete(q.id)} />
                                )}
                            </td>
                            <td>{q.writer}</td>
                            <td>{q.createdAt?.slice(0, 10)}</td>
                        </tr>
                        {expandedIds.includes(q.id) && (user.id === q.writerId || isAdmin) && (
                            <tr className='ask-content-row'>
                                <td className='ask-title' colSpan={5}>
                                    <div >
                                       문의내용:<div dangerouslySetInnerHTML={{ __html: q.content }} />
                                    </div>
                                </td>
                            </tr>
                        )}

                        {/* ✅ 답변이 있을 경우 */}
                        {q.answer && (user.id === q.writerId || isAdmin) && (
                            <>
                                <tr className='ask-answer-title'>
                                    <td colSpan={5} onClick={() => toggleAnswerExpand(q.id)}>
                                        <div>
                                             ᄂ 관리자 답변 {answerExpandedIds.includes(q.id) ? '숨기기 ▲' : '보기 ▼'}</div>
                                    </td>
                                </tr>
                                {answerExpandedIds.includes(q.id) && (
                                    <tr className='ask-answer-row'>
                                        <td colSpan={5}>
                                            <div>
                                            답변내용 : <div dangerouslySetInnerHTML={{ __html: q.answer }} />
                                            </div>
                                        </td>
                                    </tr>
                                )}
                            </>
                        )}

                        {/* ✅ 답변 폼은 문의글 펼치지 않아도 보여짐 */}
                        {isAdmin && (!q.answer || q.answer.trim() === '') && replyFormsVisible.includes(q.id) && (
                            <tr>
                                <td colSpan={5}>
                                    <form onSubmit={(e) => handleReplySubmit(e, q.id)}>
                                        <textarea
                                            className="ask-textarea"
                                            value={replyContent[q.id] || ''}
                                            onChange={(e) =>
                                                setReplyContent({ ...replyContent, [q.id]: e.target.value })}
                                            placeholder="답변을 입력하세요"
                                        />
                                        <button type="submit" className="reply-submit-btn">답변 등록</button>
                                    </form>
                                </td>
                            </tr>
                        )}
                    </React.Fragment>
                ))}
                </tbody>
            </table>

            {/* 페이지네이션 */}
            <div style={{ textAlign: 'center', marginTop: '20px' }}>
                <button
                    className="pagination-btn"
                    disabled={currentPage === 0}
                    onClick={() => setCurrentPage(currentPage - 1)}>
                    &lt;
                </button>
                {Array.from({ length: totalPages }, (_, i) => (
                    <button
                        key={i}
                        className={`pagination-btn ${i === currentPage ? 'active' : ''}`}
                        onClick={() => setCurrentPage(i)}>
                        {i + 1}
                    </button>
                ))}
                <button
                    className="pagination-btn"
                    disabled={currentPage === totalPages - 1}
                    onClick={() => setCurrentPage(currentPage + 1)}>
                     &gt;
                </button>
            </div>
        </div>
    );
};

export default Ask;