package bit.bitgroundspring.entity;

public enum Category {
    CHAT, INFO, QUESTION;
    
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
}
