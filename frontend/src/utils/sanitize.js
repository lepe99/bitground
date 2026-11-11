import DOMPurify from 'dompurify';

// Quill 에디터가 만드는 서식만 허용하고 스크립트·이벤트 속성 등은 제거한다 (서버 HtmlSanitizer와 같은 허용 목록)
const ALLOWED_ATTRS = {
    a: ['href', 'target', 'rel'],
    img: ['src', 'alt', 'width', 'height', 'style'],
    span: ['class'],
    li: ['class', 'data-list'],
    p: ['class'],
};
// 이미지 크기 조절(Quill 리사이즈)이 남기는 width·height만 남긴다
const IMG_STYLES = {width: /^\d+(px|%)$/, height: /^(auto|\d+(px|%))$/};

// 다른 곳의 DOMPurify 설정과 섞이지 않게 별도 인스턴스에 훅을 건다
const purify = DOMPurify();

purify.addHook('uponSanitizeAttribute', (node, data) => {
    const allowed = ALLOWED_ATTRS[node.nodeName.toLowerCase()] ?? [];
    if (!allowed.includes(data.attrName)) {
        data.keepAttr = false;
        return;
    }
    // DOMPurify는 img의 data: 주소를 URI 규칙과 별개로 허용한다. 서버(jsoup)처럼 http·https만 남긴다
    if ((data.attrName === 'src' || data.attrName === 'href') && /^\s*data:/i.test(data.attrValue)) {
        data.keepAttr = false;
        return;
    }
    if (data.attrName === 'style') {
        const kept = data.attrValue.split(';')
            .map((decl) => decl.split(':').map((part) => part.trim()))
            .filter(([prop, value]) => IMG_STYLES[prop?.toLowerCase()]?.test(value ?? ''))
            .map(([prop, value]) => `${prop.toLowerCase()}:${value}`);
        if (kept.length) data.attrValue = kept.join(';');
        else data.keepAttr = false;
    }
});

// 링크는 항상 새 창 + opener 차단
purify.addHook('afterSanitizeAttributes', (node) => {
    if (node.nodeName === 'A') {
        node.setAttribute('target', '_blank');
        node.setAttribute('rel', 'noopener noreferrer');
    }
});

const OPTIONS = {
    ALLOWED_TAGS: ['p', 'br', 'h1', 'h2', 'h3', 'strong', 'b', 'em', 'i', 'u', 's',
        'ol', 'ul', 'li', 'blockquote', 'pre', 'code', 'span', 'a', 'img'],
    ALLOWED_ATTR: [...new Set(Object.values(ALLOWED_ATTRS).flat())],
    // http·https와 주소만 있는 상대 경로만 (javascript:·data: 등은 제거)
    ALLOWED_URI_REGEXP: /^(?:https?:|[^a-z]|[a-z+.-]+(?:[^a-z+.\-:]|$))/i,
};

export const sanitize = (html) => purify.sanitize(html ?? '', OPTIONS);
