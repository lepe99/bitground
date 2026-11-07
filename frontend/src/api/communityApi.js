import api from './axiosConfig.js';

// 게시글
// answered: 문의 탭의 답변 완료(true)·대기(false). 비우면 전체
export const fetchPosts = async ({page, size, category, sort, answered}) =>
    (await api.get('/posts/list', {params: {page, size, category, sort, answered}})).data;

export const fetchPost = async (postId) => (await api.get(`/posts/${postId}`)).data;

// inquiryType: 문의 글만 (BUG·ACCOUNT·SUGGESTION·ETC)
export const createPost = async ({title, content, category, inquiryType}) =>
    (await api.post('/posts/form', {title, content, category, inquiryType})).data;

export const deletePost = (postId) => api.delete(`/posts/${postId}`);

// 댓글
export const fetchComments = async (postId) => (await api.get(`/comments/post/${postId}`)).data;


export const createComment = ({postId, content, parentId = null}) =>
    api.post('/comments', {postId, content, parentId});

export const deleteComment = (commentId) => api.delete(`/comments/${commentId}`);

// 좋아요·싫어요 (사용자는 서버가 토큰으로 정함)
export const sendReaction = ({targetType, targetId, liked}) =>
    api.post('/reactions', {targetType, targetId, liked});
