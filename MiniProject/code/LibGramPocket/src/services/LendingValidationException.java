package services;

import java.util.List;

public final class LendingValidationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String[] failures;

    public LendingValidationException(List<String> failures) {
        super(String.join("\n", failures));
        this.failures = failures.toArray(String[]::new);
    }

    public List<String> failures() {
        return List.of(failures);
    }
}