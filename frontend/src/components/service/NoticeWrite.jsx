import React, { useCallback, useMemo, useRef, useState } from 'react';
import ReactQuill from 'react-quill-new';
import 'react-quill-new/dist/quill.snow.css';
import '../../styles/service/service.css';
import api from '../../api/axiosConfig.js';
import { useAuth } from '../../auth/useAuth.js';
import {useToast} from "../Toast.jsx";

const NoticeWrite = ({ setSelectedMenu }) => {
    const quillRef = useRef(null);
    const [title, setTitle] = useState('');
    const [content, setContent] = useState('');
    const { user } = useAuth();
    const { errorAlert, infoAlert } = useToast();

    // 🔸 이미지 업로드 핸들러
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
                    const res = await api.post('/notices/upload-image', formData, {
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

                    // 스타일 조정
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

    // 🔸 Quill 모듈 설정
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

    // 🔸 등록 버튼 클릭 시
    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!title.trim() || !content.trim()) {
            errorAlert('제목과 내용을 입력해주세요.');
            return;
        }

        const formData = {
            userId: user.id, // 중요!
            title,
            content,
        };

        try {
            await api.post('/notices', formData, {
                headers: { 'Content-Type': 'application/json' }
            });
            infoAlert('공지사항이 등록되었습니다!');
            localStorage.setItem("serviceMenu", "notice");
            setSelectedMenu('notice');
        } catch {
            errorAlert('등록 실패');
        }
    };

    return (
        <div>
            <div className='notice-write-container'>
                <div className='notice-writer-info'>
                    <div className='notice-writer-profile'>
                        <img src={user.profileImage} alt="프로필" className='profile-image' />
                        <div className='notice-writer-details'>
                            <span className='notice-writer-tier'>[Admin]</span>
                            <span className='notice-writer-nickname'>{user.name}</span>
                        </div>
                    </div>
                </div>

                <form onSubmit={handleSubmit}>
                    <div className='notice-write-header'>
                        <input
                            type='text'
                            className='notice-write-title'
                            placeholder='제목을 입력하세요'
                            value={title}
                            onChange={(e) => setTitle(e.target.value)}
                        />
                    </div>

                    <div className='notice-write-body'>
                        <ReactQuill
                            ref={quillRef}
                            value={content}
                            onChange={setContent}
                            modules={quillModules}
                            theme='snow'
                            className='ReactQuill'
                        />

                        <div className='notice-write-footer'>
                            <button type='submit' className='notice-submit-button'>등록하기</button>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    );
};

export default NoticeWrite;