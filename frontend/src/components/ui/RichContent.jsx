import {sanitize} from '../../utils/sanitize.js';

/** 에디터로 쓴 글 본문 (허용한 서식만 남기고 표시). 서식은 index.css의 .rich */
export default function RichContent({html, className = ''}) {
    return <div className={`rich ${className}`} dangerouslySetInnerHTML={{__html: sanitize(html)}}/>;
}
