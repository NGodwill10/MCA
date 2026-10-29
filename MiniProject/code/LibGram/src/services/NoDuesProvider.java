package services;

import java.math.BigDecimal;

public final class NoDuesProvider implements DuesProvider {
    @Override
    public BigDecimal unpaidBalance(String memberId) {
        return BigDecimal.ZERO.setScale(2);
    }
}
