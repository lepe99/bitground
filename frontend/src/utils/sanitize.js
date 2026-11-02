import sanitizeHtml from 'sanitize-html';

// Quill 에디터가 만드는 서식만 허용하고 스크립트·이벤트 속성 등은 제거한다.
const OPTIONS = {
    allowedTags: [
        'p', 'br', 'h1', 'h2', 'h3', 'strong', 'b', 'em', 'i', 'u', 's',
        'ol', 'ul', 'li', 'blockquote', 'pre', 'code', 'span', 'a', 'img',
    ],
    allowedAttributes: {
        a: ['href', 'target', 'rel'],
        img: ['src', 'alt', 'width', 'height', 'style'],
        span: ['class'],
        li: ['class', 'data-list'],
        p: ['class'],
    },
    allowedSchemes: ['http', 'https'],
    allowedStyles: {
        img: { width: [/^\d+(px|%)$/], height: [/^(auto|\d+(px|%))$/] },
    },
    transformTags: {
        a: sanitizeHtml.simpleTransform('a', { target: '_blank', rel: 'noopener noreferrer' }),
    },
};

export const sanitize = (html) => sanitizeHtml(html ?? '', OPTIONS);
