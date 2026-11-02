import React, { useEffect, useState } from 'react';
import { RiDeleteBinLine } from "react-icons/ri";
import api from '../../api/axiosConfig';
import { useAuth } from '../../auth/useAuth';

const Notice = ({keyword}) => {
    const [notices, setNotices] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [openIds, setOpenIds] = useState([]); // 토글용 상태
    const { user } = useAuth();
    const { infoAlert, errorAlert } = useAuth();

    const fetchNotices = async (pageNum = 0) => {
        try {
            const res = await api.get('/notices', {
                params: { page: pageNum, size: 10, keyword: keyword || ''},
            });
            setNotices(res.data.content);
            setTotalPages(res.data.totalPages);
            setPage(res.data.number);
            setOpenIds([]); // 페이지 변경 시 열려있는 토글 초기화
        } catch (error) {
            console.error("공지사항 불러오기 실패", error);
        }
    };

    const getPageNumbers = () => {
        const pageNumbers = [];
        const visiblePages = 5; // 보여줄 페이지 버튼 수
        let start = Math.max(0, page - Math.floor(visiblePages / 2));
        let end = Math.min(totalPages, start + visiblePages);

        if (end - start < visiblePages) {
            start = Math.max(0, end - visiblePages);
        }

        for (let i = start; i < end; i++) {
            pageNumbers.push(i);
        }
        return pageNumbers;
    };

    useEffect(() => {
        fetchNotices(0); // 첫 페이지 로딩
    }, [keyword]);

    const handlePageChange = (newPage) => {
        if (newPage >= 0 && newPage < totalPages) {
            fetchNotices(newPage);
        }
    };

    const toggleNotice = (id) => {
        setOpenIds(prev =>
            prev.includes(id) ? prev.filter(n => n !== id) : [...prev, id]
        );
    };

    const handleDelete = async (id) => {
        if (window.confirm("정말 삭제하시겠습니까?")) {
            try {
                await api.delete(`/notices/${id}`);
                infoAlert("삭제되었습니다.");
                setNotices(prev => prev.filter(notice => notice.id !== id));
            } catch (err) {
                errorAlert("삭제 실패: " + err.message);
            }
        }
    };



    return (
        <div className='notice-list'>
            <table className='notice-table'>
                <colgroup>
                    <col style={{ width: '5%' }} />
                    <col style={{ width: '60%' }} />
                    <col style={{ width: '10%' }} />
                    <col style={{ width: '15%' }} />
                    <col style={{ width: '10%' }} />
                </colgroup>
                <thead>
                <tr className='notice-table-head'>
                    <th>번호</th>
                    <th>제목</th>
                    <th></th>
                    <th>작성자</th>
                    <th>등록일</th>
                </tr>
                </thead>
                <tbody>
                {notices.map((notice) => (
                    <React.Fragment key={notice.id}>
                        <tr className='notice-row'>
                            <td>{notice.id}</td>
                            <td
                                className='notice-title'
                                onClick={() => toggleNotice(notice.id)}
                            >
                                {notice.title}
                            </td>
                            <td>
                                {user?.role === 'ROLE_ADMIN' && <RiDeleteBinLine className='delicon' onClick={() => handleDelete(notice.id)} />}
                            </td>
                            <td>{notice.writer}</td>
                            <td>{notice.createdAt?.substring(0, 10)}</td>
                        </tr>
                        {openIds.includes(notice.id) && (
                            <tr>
                                <td className="ask-title" colSpan="5">
                                    <div>
                                        공지사항 : <div dangerouslySetInnerHTML={{ __html: notice.content }} />
                                    </div>
                                </td>
                            </tr>
                        )}
                    </React.Fragment>
                ))}
                </tbody>
                <tfoot>
                <tr>
                    <td colSpan="5" style={{ textAlign: 'center', padding: '20px' }}>
                        <button
                            className='pagination-btn'
                            onClick={() => handlePageChange(page - 1)}
                            disabled={page === 0}
                        >
                            &lt;
                        </button>

                        {getPageNumbers().map((p) => (
                            <button
                                key={p}
                                className={`pagination-btn ${p === page ? 'active' : ''}`}
                                onClick={() => handlePageChange(p)}
                            >
                                {p + 1}
                            </button>
                        ))}

                        <button
                            className='pagination-btn'
                            onClick={() => handlePageChange(page + 1)}
                            disabled={page === totalPages - 1}
                        >
                            &gt;
                        </button>
                    </td>
                </tr>
                </tfoot>
            </table>
        </div>
    );
};

export default Notice;