package bit.bitgroundspring.entity;

public enum Role {
    ROLE_ADMIN,
    ROLE_USER;

    public boolean isAdmin() {
        return this == ROLE_ADMIN;
    }
}
