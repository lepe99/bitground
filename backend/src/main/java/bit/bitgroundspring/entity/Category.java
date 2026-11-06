package bit.bitgroundspring.entity;

public enum Category {
    CHAT, INFO, QUESTION,
    /** 문의: 작성자와 관리자만 보는 비공개 글. 전체 목록에 섞이지 않는다 */
    INQUIRY;
    
    /**
     * 편의 메서드들
     */
    public boolean isChat() {
        return this == CHAT;
    }
    public boolean isInfo() {
        return this == INFO;
    }
    public boolean isQuestion() {
        return this == QUESTION;
    }
    /** 작성자와 관리자만 볼 수 있는 분류 */
    public boolean isPrivate() {
        return this == INQUIRY;
    }
}
