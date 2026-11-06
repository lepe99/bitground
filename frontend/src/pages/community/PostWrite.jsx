import {useState} from "react";
import {useNavigate, useSearchParams} from "react-router-dom";
import {useToast} from "../../components/toastContext.js";
import {createPost} from "../../api/communityApi.js";
import {getErrorMessage} from "../../api/apiError.js";
import Card from "../../components/ui/Card.jsx";
import Button from "../../components/ui/Button.jsx";
import SegmentedTabs from "../../components/ui/SegmentedTabs.jsx";
import RichEditor from "../../components/ui/RichEditor.jsx";
import {CATEGORIES, INQUIRY, INQUIRY_TYPES} from "../../components/community/post.js";

const WRITE_CATEGORIES = CATEGORIES.filter((c) => c.value !== "ALL");

/** 글쓰기: 분류 · (문의면 유형) · 제목 · 본문(사진 첨부). 문의 탭에서 오면 문의로 시작한다 */
export default function PostWrite() {
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const {infoAlert, errorAlert} = useToast();
    const [category, setCategory] = useState(params.get("category") === INQUIRY ? INQUIRY : "CHAT");
    const [inquiryType, setInquiryType] = useState(null);
    const inquiry = category === INQUIRY;
    const [title, setTitle] = useState("");
    const [content, setContent] = useState("");
    const [saving, setSaving] = useState(false);

    const submit = async (e) => {
        e.preventDefault();
        // 에디터는 빈 글도 <p><br></p>로 남기므로 태그를 뺀 글자와 사진으로 확인한다
        const empty = !content.replace(/<[^>]*>/g, "").trim() && !content.includes("<img");
        if (inquiry && !inquiryType) return errorAlert("문의 유형을 선택해주세요.");
        if (!title.trim() || empty) return errorAlert("제목과 내용을 입력해주세요.");
        setSaving(true);
        try {
            const created = await createPost({title: title.trim(), content, category, inquiryType: inquiry ? inquiryType : null});
            infoAlert(inquiry ? "문의를 등록했어요. 답변이 달리면 알려 드릴게요." : "글을 등록했어요.");
            navigate(created?.id ? `/community/${created.id}` : "/community");
        } catch (err) {
            errorAlert(getErrorMessage(err, "글을 등록하지 못했습니다."));
            setSaving(false);
        }
    };

    return (
        <div className="mx-auto flex max-w-[800px] flex-col gap-4 px-5 pt-4 pb-12 md:gap-5 md:pt-8">
            <h1 className="text-xl font-extrabold md:text-[28px]">{inquiry ? "문의하기" : "글쓰기"}</h1>
            <Card as="form" onSubmit={submit} className="flex flex-col gap-4 p-5 md:p-7">
                <SegmentedTabs label="분류" items={WRITE_CATEGORIES} value={category} onChange={setCategory} className="w-fit"/>
                {inquiry && (
                    <div className="flex flex-col gap-3 rounded-2xl bg-surface-2 p-4">
                        <SegmentedTabs label="문의 유형" items={INQUIRY_TYPES} value={inquiryType} onChange={setInquiryType} className="w-fit"/>
                        <p className="text-xs leading-relaxed text-fg-2">
                            문의는 나와 관리자만 볼 수 있고, 답변은 댓글로 달려요.<br/>
                            첨부한 사진은 주소를 아는 사람이면 볼 수 있으니 개인정보가 보이지 않게 가려 주세요.
                        </p>
                    </div>
                )}
                <input
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder="제목"
                    aria-label="제목"
                    maxLength={100}
                    className="h-12 rounded-xl border border-line bg-surface px-4 text-base font-semibold outline-none focus:border-fg-3"
                />
                <RichEditor
                    value={content}
                    onChange={setContent}
                    placeholder={inquiry
                        ? "어떤 화면에서 무엇을 했을 때 어떤 일이 있었는지 적어 주시면 빨리 확인할 수 있어요."
                        : "자유롭게 이야기를 나눠 보세요. 투자 권유나 비방은 삭제될 수 있어요."}
                />
                <div className="flex justify-end gap-2">
                    <Button variant="secondary" onClick={() => navigate(-1)}>취소</Button>
                    <Button type="submit" disabled={saving}>{saving ? "등록 중…" : "등록"}</Button>
                </div>
            </Card>
        </div>
    );
}
