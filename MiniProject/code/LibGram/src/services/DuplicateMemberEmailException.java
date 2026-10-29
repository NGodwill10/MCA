package services;

import model.Member;

public final class DuplicateMemberEmailException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final transient Member existingMember;

    public DuplicateMemberEmailException(Member existingMember) {
        super("A member with this email already exists: " + existingMember.name() + ".");
        this.existingMember = existingMember;
    }

    public Member existingMember() {
        return existingMember;
    }
}