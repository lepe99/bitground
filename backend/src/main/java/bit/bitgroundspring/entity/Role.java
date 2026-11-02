package bit.bitgroundspring.entity;

import lombok.Getter;

@Getter
public enum Role {
    ROLE_ADMIN,
    ROLE_USER;
    
    
    /**
     * 권한 확인 편의 메서드들
     */
    public boolean isAdmin() {
        return this == ROLE_ADMIN;
    }
    
    public boolean isUser() {
        return this == ROLE_USER;
    }
}