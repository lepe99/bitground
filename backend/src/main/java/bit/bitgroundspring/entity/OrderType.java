package bit.bitgroundspring.entity;

public enum OrderType {
    BUY, SELL;

    /**
     * 편의 메서드들
     */
    public boolean isBuy() {
        return this == BUY;
    }

    public boolean isSell() {
        return this == SELL;
    }
}
