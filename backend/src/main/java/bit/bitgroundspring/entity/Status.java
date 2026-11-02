package bit.bitgroundspring.entity;

import lombok.Getter;

@Getter
public enum Status {
    PENDING, COMPLETED;
    
    
    /**
     * 편의 메서드들
     */
    public boolean isPending() {
        return this == PENDING;
    }
    
    public boolean isCompleted() {
        return this == COMPLETED;
    }
}