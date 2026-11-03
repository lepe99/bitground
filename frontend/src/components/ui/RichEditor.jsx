import {useCallback, useMemo, useRef} from 'react';
import ReactQuill from 'react-quill-new';
import 'react-quill-new/dist/quill.snow.css';
import {uploadImage} from '../../api/uploadApi.js';
import {useToast} from '../Toast.jsx';
import {getErrorMessage} from '../../api/apiError.js';

const TOOLBAR = [
    [{header: [1, 2, false]}],
    ['bold', 'italic', 'underline', 'strike'],
    [{list: 'ordered'}, {list: 'bullet'}],
    ['link', 'image'],
    ['clean'],
];

/**
 * 글쓰기 에디터 (Quill). 이미지는 서버에 올린 뒤 주소를 넣는다. 테마 색은 index.css의 .rich-editor에서 맞춘다
 */
export default function RichEditor({value, onChange, placeholder}) {
    const quillRef = useRef(null);
    const {errorAlert} = useToast();

    const insertImage = useCallback(() => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = async () => {
            const file = input.files?.[0];
            if (!file) return;
            try {
                const url = await uploadImage(file);
                const editor = quillRef.current?.getEditor();
                if (!editor) return;
                const range = editor.getSelection(true) ?? {index: editor.getLength()};
                editor.insertEmbed(range.index, 'image', url);
                editor.setSelection(range.index + 1, 0);
            } catch (error) {
                errorAlert(getErrorMessage(error, '이미지를 올리지 못했습니다.'));
            }
        };
        input.click();
    }, [errorAlert]);

    const modules = useMemo(() => ({toolbar: {container: TOOLBAR, handlers: {image: insertImage}}}), [insertImage]);

    return (
        <div className="rich-editor">
            <ReactQuill ref={quillRef} theme="snow" value={value} onChange={onChange} modules={modules} placeholder={placeholder}/>
        </div>
    );
}
