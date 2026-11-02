import React, { useCallback, useMemo, useRef, useState } from 'react';
import ReactQuill from 'react-quill-new';
import 'react-quill-new/dist/quill.snow.css';
import '../../styles/service/service.css';
import api from '../../api/axiosConfig.js';
import { useAuth } from '../../auth/useAuth.js';
import { tierImageMap } from '../community/tierImageUtil';
import {useToast} from "../Toast.jsx";

const AskWrite = ( { setSelectedMenu } ) => {
    const quillRef = useRef(null);
    const [title, setTitle] = useState('');
    const [content, setContent] = useState('');
    const { user } = useAuth();
    const { errorAlert, infoAlert } = useToast();

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
                    const res = await api.post('/inquiries/upload-image', formData, {
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
        console.log('user', user);
        const formData = {
            user: {id :user.id},
            title,
            content,
        };

        try {
            await api.post('/inquiries', formData, {
                headers: { 'Content-Type': 'application/json' }
            });
            infoAlert('문의글이 등록되었습니다!');
            localStorage.setItem("serviceMenu", "ask"); // 다음 진입 시 탭 고정용
            setSelectedMenu('ask');
        } catch {
            errorAlert('등록 실패');
        }
    };

    return (
        <div>
            <div className='ask-write-container'>
                <div className='ask-writer-info'>
                    <div className='ask-writer-profile'>
                        <div className="ask-user-icon-div">
                            <div className="ask-user-icon">
                                {user.tier !== 0 ? (
                                    <>
                                        <img
                                            src={tierImageMap[user.tier]}
                                            alt=""
                                            className="ask-tier-image"
                                        />
                                        {user.profileImage && (
                                            <img
                                                src={user.profileImage}
                                                alt=""
                                                className="ask-rank-profile-image"
                                            />
                                        )}
                                    </>
                                ) : (
                                    user.profileImage && (
                                        <img
                                            src={user.profileImage}
                                            alt=""
                                            className="ask-rank-profile-image"
                                        />
                                    )
                                )}
                            </div>
                        </div>
                        <div className='ask-writer-details'>
                            <span className='ask-writer-nickname'>{user.name}</span>
                        </div>
                    </div>
                </div>

                <form onSubmit={handleSubmit}>
                    <div className='ask-write-header'>
                        <input
                            type='text'
                            className='ask-write-title'
                            placeholder='제목을 입력하세요'
                            value={title}
                            onChange={(e) => setTitle(e.target.value)}
                        />
                    </div>
                    <div className='ask-write-body'>
                        <ReactQuill
                            ref={quillRef}
                            value={content}
                            onChange={setContent}
                            modules={quillModules}
                            theme='snow'
                            className='ReactQuill-ask'

                        />
                    </div>
                    <div className='ask-write-footer'>
                        <button type='submit' className='ask-submit-button'>등록하기</button>
                    </div>
                </form>
            </div>
        </div>
    );
};

export default AskWrite;