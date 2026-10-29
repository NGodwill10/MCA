package services;

import java.math.BigDecimal;

@FunctionalInterface
public interface DuesProvider {
    BigDecimal unpaidBalance(String memberId);
}
