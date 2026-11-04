import {useEffect} from 'react';
import {SITE} from '../../constants/site.js';

/**
 * 약관·개인정보처리방침 공통 틀. 로그인 없이 볼 수 있다 (OAuth 앱 검증용 공개 페이지)
 * @param sections [{title, body: ReactNode}] — 제목 앞 번호는 자동으로 붙는다
 */
export default function LegalDocument({title, intro, sections}) {
    useEffect(() => {
        const prev = document.title;
        document.title = `${title} - ${SITE.name}`;
        return () => {
            document.title = prev;
        };
    }, [title]);

    return (
        <article className="mx-auto flex max-w-[760px] flex-col gap-8 px-5 py-10 md:py-14">
            <header className="flex flex-col gap-3 border-b border-line pb-6">
                <h1 className="text-2xl font-extrabold tracking-[-0.02em] md:text-[28px]">{title}</h1>
                <p className="text-sm leading-relaxed text-fg-2">{intro}</p>
                <p className="text-xs text-fg-3">시행일 {SITE.effectiveDate}</p>
            </header>

            <nav aria-label="목차" className="rounded-2xl bg-surface p-5 text-sm">
                <ol className="grid gap-1.5 md:grid-cols-2">
                    {sections.map((s, i) => (
                        <li key={s.title}>
                            <a href={`#section-${i + 1}`} className="text-fg-2 hover:text-fg hover:underline">{i + 1}. {s.title}</a>
                        </li>
                    ))}
                </ol>
            </nav>

            {sections.map((s, i) => (
                <section key={s.title} id={`section-${i + 1}`} className="flex scroll-mt-20 flex-col gap-3">
                    <h2 className="text-lg font-bold">{i + 1}. {s.title}</h2>
                    <div className="flex flex-col gap-2.5 text-[15px] leading-relaxed text-fg-2 [&_li]:ml-5 [&_li]:list-disc [&_strong]:font-semibold [&_strong]:text-fg [&_table]:w-full [&_table]:text-sm [&_td]:border [&_td]:border-line [&_td]:p-2.5 [&_td]:align-top [&_th]:border [&_th]:border-line [&_th]:bg-surface-2 [&_th]:p-2.5 [&_th]:text-left [&_ul]:flex [&_ul]:flex-col [&_ul]:gap-1">
                        {s.body}
                    </div>
                </section>
            ))}
        </article>
    );
}
