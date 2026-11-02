import api from './axiosConfig.js';

// 게시글
export const fetchPosts = async ({page, size, category, sort}) =>
    (await api.get('/posts/list', {params: {page, size, category, sort}})).data;

export const fetchPost = async (postId) => (await api.get(`/posts/${postId}`)).data;

export const createPost = async ({title, content, category}) =>
    (await api.post('/posts/form', {title, content, category})).data;

export const deletePost = (postId) => api.delete(`/posts/${postId}`);

// 댓글
export const fetchComments = async (postId) => (await api.get(`/comments/post/${postId}`)).data;

export const fetchCommentCount = async (postId) => (await api.get(`/comments/post/${postId}/count`)).data;

export const createComment = ({postId, content, parentId = null}) =>
    api.post('/comments', {postId, content, parentId});

export const deleteComment = (commentId) => api.delete(`/comments/${commentId}`);

// 좋아요·싫어요 (사용자는 서버가 토큰으로 정함)
export const sendReaction = ({targetType, targetId, liked}) =>
    api.post('/reactions', {targetType, targetId, liked});
