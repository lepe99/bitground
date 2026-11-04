import {useId} from 'react';

/**
 * 기본 프로필 사진. 소셜 로그인에서 사진을 받지 못했거나 사진을 지웠을 때 쓴다.
 * 테마 색(브랜드 연한 바탕 + 브랜드 실루엣)을 따라 밝은/어두운 화면 모두에서 자연스럽게 보인다
 */
export default function DefaultAvatar({className = ''}) {
    const clipId = 'avatar-clip-' + useId().replace(/[^a-zA-Z0-9_-]/g, '');
    return (
        <svg viewBox="0 0 48 48" aria-hidden="true" className={`size-full rounded-full ${className}`}>
            <defs>
                <clipPath id={clipId}>
                    <circle cx="24" cy="24" r="24"/>
                </clipPath>
            </defs>
            <g clipPath={`url(#${clipId})`}>
                <rect width="48" height="48" className="fill-brand-soft"/>
                <circle cx="24" cy="19" r="8.5" className="fill-brand" opacity="0.85"/>
                <path d="M6 50c0-10.5 8-17.5 18-17.5S42 39.5 42 50z" className="fill-brand" opacity="0.85"/>
            </g>
        </svg>
    );
}
