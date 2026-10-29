package services;

import model.Member;

public final class DuplicateMemberPhoneException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final transient Member existingMember;

    public DuplicateMemberPhoneException(Member existingMember) {
        super("A member with this phone number already exists: " + existingMember.name() + ". Add anyway?");
        this.existingMember = existingMember;
    }

    public Member existingMember() {
        return existingMember;
    }
}