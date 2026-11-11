import Avatar from '../ui/Avatar.jsx';

/**
 * 프로필 사진 고르기 / 기본 사진으로 되돌리기
 * @param preview 보여줄 사진 주소 (비어 있으면 기본 프로필 사진)
 */
export default function ProfileImagePicker({preview, tier, onPick, onReset, size = 'xl', className = ''}) {
    return (
        <div className={`flex items-center gap-4 ${className}`}>
            <Avatar src={preview} tier={tier} size={size} badge={false}/>
            <div className="flex flex-col gap-1.5">
                <div className="flex flex-wrap gap-1.5">
                    <label className="inline-flex h-9 w-fit cursor-pointer items-center rounded-[10px] border border-line px-3.5 text-[13px] font-semibold focus-within:outline-2 focus-within:outline-brand hover:bg-surface-2">
                        사진 바꾸기
                        <input
                            type="file"
                            accept="image/*"
                            onChange={(e) => {
                                onPick(e.target.files?.[0]);
                                e.target.value = ''; // 같은 파일을 다시 골라도 반영되게
                            }}
                            className="sr-only"
                        />
                    </label>
                    {preview && (
                        <button type="button" onClick={onReset} className="h-9 rounded-[10px] px-3 text-[13px] font-semibold text-fg-3 hover:bg-surface-2 hover:text-fg-2">
                            사진 삭제
                        </button>
                    )}
                </div>
                <span className="text-xs text-fg-3">{preview ? '삭제하면 기본 프로필 사진으로 바뀌어요' : '5MB 이하 이미지'}</span>
            </div>
        </div>
    );
}
