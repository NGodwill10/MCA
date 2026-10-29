package services;

import java.math.BigDecimal;

public final class RealDuesProvider implements DuesProvider {
    private final ChargeService charges;
    public RealDuesProvider(ChargeService charges) { this.charges = charges; }
    @Override public BigDecimal unpaidBalance(String memberId) { return charges.unpaidBalance(memberId); }
}
