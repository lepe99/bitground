import React, { useCallback, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ReactQuill from 'react-quill-new';
import 'react-quill-new/dist/quill.snow.css';
import '../../styles/community/post.css';
import api from '../../api/axiosConfig.js';
import { useAuth } from '../../auth/useAuth.js';
import { tierImageMap } from './tierImageUtil';
import {useToast} from "../Toast.jsx";

const PostWrite = () => {
    const navigate = useNavigate();
    const quillRef = useRef(null);
    const [title, setTitle] = useState('');
    const [content, setContent] = useState('');
    const [category, setCategory] = useState('CHAT');
    const user = useAuth();
    const {errorAlert, infoAlert} = useToast();


    const imageHandler = useCallback(() => {
        const input = document.createElement('input');
        input.setAttribute('type', 'file');
        input.setAttribute('accept', 'image/*');
        input.click();

        input.onchange = async () => {
            const file = input.files[0];
            if (file) {
                const formData = new FormData();
                formData.append('file', file);
                try {
                    const res = await api.post('/posts/upload-image', formData, {
                        headers: { 'Content-Type': 'multipart/form-data' },
                    });

                    const imageUrl = res.data.url;
                    const editor = quillRef.current?.getEditor();
                    let range = editor?.getSelection();
                    if (!range) {
                        const length = editor.getLength();
                        editor.setSelection(length, 0);
                        range = editor.getSelection();
                    }
                    editor.insertEmbed(range.index, 'image', imageUrl);

                    setTimeout(() => {
                        const editorElem = quillRef.current.editor?.root;
                        const imgs = editorElem?.querySelectorAll(`img[src="${imageUrl}"]`);
                        imgs?.forEach(img => {
                            img.style.width = '300px';
                            img.style.height = 'auto';
                            img.setAttribute('width', '300px');
                            img.setAttribute('height', 'auto');
                        });
                    }, 0);
                } catch (err) {
                    console.error('이미지 업로드 실패', err);
                }
            }
        };
    }, []);

    const quillModules = useMemo(() => ({
        toolbar: {
            container: [
                [{ header: [1, 2, false] }],
                ['bold', 'italic', 'underline', 'strike'],
                [{ list: 'ordered' }, { list: 'bullet' }],
                ['link', 'image'],
                ['clean']
            ],
            handlers: { image: imageHandler },
        }
    }), [imageHandler]);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!title.trim() || !content.trim()) {
            errorAlert('제목과 내용을 입력해주세요.');
            return;
        }

        const formData = {
            user: { id: user.user.id },
            title,
            content,
            category
        };

        try {
            await api.post('/posts/form', formData, {
                headers: { 'Content-Type': 'application/json' }
            });
            infoAlert('게시글이 등록되었습니다!');
            navigate('/community');
        } catch {
            errorAlert('등록 실패');
        }
    };

    return (
        <div className="post-container">
            <div className='write-container'>
                <div className='writer-info'>
                    <div className='writer-profile'>
                        <div className="user-icon-div">
                            <div className="post-user-icon">
                                {user.user.tier !== 0 ? (
                                <>
                                <img
                                    src={tierImageMap[user.user.tier]}
                                    alt=""
                                    className="post-tier-image"
                                />
                                {user.user.profileImage && (
                                    <img
                                        src={user.user.profileImage}
                                        alt=""
                                        className="post-rank-profile-image"
                                    />
                                )}
                                </>
                                ) : (user.user.profileImage && (
                                    <img
                                        src={user.user.profileImage}
                                        alt=""
                                        className="post-rank-profile-image"
                                    />
                                )
                                )}
                            </div>
                        </div>
                        <div className='writer-details'>
                            <span className='writer-nickname'>{user.user.name}</span>
                        </div>
                    </div>
                    <select
                        value={category}
                        onChange={(e) => setCategory(e.target.value)}
                        className='category-select'
                    >
                        <option value="CHAT">CHAT</option>
                        <option value="INFO">INFO</option>
                        <option value="QUESTION">QUESTION</option>
                    </select>
                </div>

                <form onSubmit={handleSubmit}>
                    <div className='write-header'>
                        <input
                            type='text'
                            className='write-title'
                            placeholder='제목을 입력하세요'
                            value={title}
                            onChange={(e) => setTitle(e.target.value)}
                        />
                    </div>
                    <div className='write-body'>
                        <ReactQuill
                            ref={quillRef}
                            value={content}
                            onChange={setContent}
                            modules={quillModules}
                            theme='snow'
                            className='ReactQuill-post'
                        />
                    </div>
                    <div className='write-footer'>
                        <button type='submit' className='submit-button'>등록하기</button>
                    </div>
                </form>
            </div>
            <div className='postheader'>
                <button type='button' className='listbtn' onClick={() => navigate('/community')}>
                    &lt; 목록
                </button>
            </div>
        </div>
    );
};

export default PostWrite;