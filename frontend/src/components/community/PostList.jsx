import "../../styles/community/post.css";
import {useNavigate} from 'react-router-dom';
import api from "../../api/axiosConfig.js";
import {useEffect, useState} from "react";
import {HiOutlinePhoto} from "react-icons/hi2";
import {tierImageMap} from "./tierImageUtil.js";

/**
 * 게시글 목록 페이지 컴포넌트
 * - 전체 게시글 목록 표시
 * - 카테고리별 필터링 기능
 * - 정렬 기능 (최신순, 오래된순, 인기순)
 * - 페이지네이션 지원
 */
const PostList = () => {
    const navigate = useNavigate();
    const [currentCategory, setCurrentCategory] = useState('전체');
    const [posts, setPosts] = useState([]);
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [sortOrder, setSortOrder] = useState("latest");

    const handleWrite = () => navigate('/community/write');

    const handlePostClick = (postId) => {
        navigate(`/community/${postId}?forceViewCount=true`);
    };

    const handleCategoryClick = (category) => {
        setCurrentCategory(category);
        setCurrentPage(0);
        fetchPosts(0, category);
    };

    const tdStyle = {
        verticalAlign: 'middle',
        textAlign: 'center',
        // height: '40px',
        // lineHeight: '40px'
    };

    const titleStyle = {
        ...tdStyle,
        textAlign: 'left',
        cursor: 'pointer',
        // maxWidth: '600px',
        overflow: 'hidden',
        // textOverflow: 'ellipsis',
        // whiteSpace: 'nowrap'
    };

    const commentStyle = {
        color: '#FC5754',
        marginLeft: '5px',
        // fontSize: '0.9em'
    };

    const imageIconStyle = {
        marginLeft: '5px',
        marginTop: '3px',
        fontSize: '1.2em',
        color: '#8c8c8c'
    };

    const categoryButtonStyle = (category) => ({
        backgroundColor: currentCategory === category ? '#FC5754' : 'white',
        color: currentCategory === category ? 'white' : '#8C8C8C',
        padding: '0 20px',
        cursor: 'pointer',
    });

    const fetchPosts = async (page = 0, category = currentCategory, sort = sortOrder) => {
        try {
            const res = await api.get(`/posts/list`, {
                params: {
                    page,
                    size: 15,
                    category: category === '전체' ? null : category,
                    sort
                }
            });
            setPosts(res.data.content);
            setTotalPages(res.data.totalPages);
        } catch (err) {
            console.error('게시글 불러오기 실패:', err);
        }
    };

    useEffect(() => {
        fetchPosts(currentPage, currentCategory, sortOrder);
    }, [currentPage, currentCategory, sortOrder]);

    const filteredPosts = currentCategory === '전체'
        ? posts
        : posts.filter(post => post.category === currentCategory);

    const handleSortChange = (e) => {
        setSortOrder(e.target.value);
    };

    const formatCreatedAt = (createdAt) => {
        if (!createdAt) return '';
        const postDate = new Date(createdAt);
        const now = new Date();
        const isToday = postDate.toDateString() === now.toDateString();
        if (isToday) {
            return `${postDate.getHours().toString().padStart(2, '0')}:${postDate.getMinutes().toString().padStart(2, '0')}`;
        } else {
            return `${(postDate.getMonth() + 1).toString().padStart(2, '0')}-${postDate.getDate().toString().padStart(2, '0')}`;
        }
    };

    return (
        <div className={"post-container"}>

            <div className='postlist'>
                <table className='posttable'>
                    <colgroup>
                        <col style={{width: '5%'}}/>
                        <col style={{width: '59%'}}/>
                        <col style={{width: '17%'}}/>
                        <col style={{width: '6%'}}/>
                        <col style={{width: '8%'}}/>
                        <col style={{width: '5%'}}/>
                    </colgroup>
                    <thead className='postbtns'>
                    <tr>
                        <th colSpan="6" style={{textAlign: 'left'}}>
                            <div className="button-group">
                                {['전체', 'CHAT', 'QUESTION', 'INFO'].map(cat => (
                                    <button
                                        key={cat}
                                        onClick={() => handleCategoryClick(cat)}
                                        style={categoryButtonStyle(cat)}>
                                        {cat === '전체' ? '전체' : cat === 'CHAT' ? '잡담' : cat === 'QUESTION' ? '질문' : '정보'}
                                    </button>
                                ))}
                            </div>
                        </th>
                        <th style={{textAlign: 'right'}}>
                            <select className="sort" onChange={handleSortChange} value={sortOrder}>
                                <option value="latest">최신순</option>
                                <option value="oldest">오래된순</option>
                                <option value="popular">인기순</option>
                                <option value="views">조회순</option>
                            </select>
                        </th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr className='posttable-body-header'>
                        <td style={tdStyle}>번호</td>
                        <td style={tdStyle}>제목</td>
                        <td style={tdStyle}>글쓴이</td>
                        <td style={tdStyle}>등록일</td>
                        <td style={tdStyle}>조회수</td>
                        <td style={tdStyle}>좋아요</td>
                        <td style={tdStyle}>싫어요</td>
                    </tr>
                    {filteredPosts.map(post => (
                        <tr key={post.id}>
                            <td style={tdStyle}>{post.id}</td>
                            <td style={titleStyle} onClick={() => handlePostClick(post.id)}>
                                [{post.category}] {post.title}
                                <span className="title-icon">
                                    <span style={commentStyle}>[{post.commentCount}]</span>
                                    {post.hasImage && <HiOutlinePhoto style={imageIconStyle}/>}
                                </span>
                            </td>
                            <td style={tdStyle}>
                                <div className="user-icon-div">
                                    <div className="post-user-icon">
                                        {post.tier !== 0 ? (
                                            <>
                                                <img
                                                    src={tierImageMap[post.tier]}
                                                    alt=""
                                                    className="post-tier-image"
                                                />
                                                {post.profileImage && (
                                                    <img
                                                        src={post.profileImage}
                                                        alt=""
                                                        className="post-rank-profile-image"
                                                    />
                                                )}
                                            </>
                                        ) : (
                                            post.profileImage && (
                                                <img
                                                    src={post.profileImage}
                                                    alt=""
                                                    className="post-rank-profile-image"
                                                />
                                            )
                                        )}
                                    </div>
                                    <span>{post.name}</span>
                                </div>
                            </td>
                            <td style={tdStyle}>{formatCreatedAt(post.createdAt)}</td>
                            <td style={tdStyle}>{post.views}</td>
                            <td style={tdStyle}>{post.likes}</td>
                            <td style={tdStyle}>{post.dislikes}</td>
                        </tr>
                    ))}

                    </tbody>
                    <tfoot>
                    <tr>
                        <td colSpan="7" style={{textAlign: 'center', padding: '30px 0 0 0'}}>
                            <button
                                className="pagination-btn"
                                disabled={currentPage === 0}
                                onClick={() => setCurrentPage(currentPage - 1)}>
                                &lt; 이전
                            </button>
                            {Array.from({length: totalPages}, (_, i) => (
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
                                다음 &gt;
                            </button>
                        </td>
                    </tr>
                    </tfoot>
                </table>
            </div>
            <div className='postheader'>
                <button type='button' className='listbtn'>&lt; 목록</button>
                &nbsp;&nbsp;
                <button type='button' className='writebtn' onClick={handleWrite}>📝 글쓰기</button>
            </div>
            {/* 모바일 카드형 UI */}
            <div className="post-card-list">
                {filteredPosts.map(post => (
                    <div
                        key={post.id}
                        className="post-card"
                        onClick={() => handlePostClick(post.id)}
                    >
                        <div className="post-card-title">
                            [{post.category}] {post.title}
                        </div>
                        <div className="post-card-userinfo">
                            <div className={"post-card-usernametag"}>
                                <div className="post-user-icon-small">
                                    {/* 티어 이미지: tier가 존재할 때만 출력 */}
                                    {post.tier !== 0 && tierImageMap[post.tier] && (
                                        <img
                                            src={tierImageMap[post.tier]}
                                            alt={`티어 ${post.tier}`}
                                            className="post-tier-image"
                                        />
                                    )}

                                    {/* 프로필 이미지는 항상 출력 (있을 경우) */}
                                    {post.profileImage && (
                                        <img
                                            src={post.profileImage}
                                            alt="프로필"
                                            className="post-rank-profile-image"
                                        />
                                    )}
                                </div>
                            <span className="post-card-author">{post.name}</span>
                            </div>
                            <div className="post-card-stats-row">
                                <div className="post-card-stats">
                                    <span>👁 {post.views}</span>
                                    <span>❤️ {post.likes}</span>
                                </div>
                                <div className="post-card-date">{formatCreatedAt(post.createdAt)}</div>
                            </div>
                        </div>
                    </div>
                ))}
                <div className="post-card-pagination">
                    <button
                        className="pagination-btn"
                        disabled={currentPage === 0}
                        onClick={() => setCurrentPage(currentPage - 1)}>
                        &lt; 이전
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
                        다음 &gt;
                    </button>
                </div>
            </div>
        </div>
    );
};

export default PostList;