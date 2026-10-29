package services;

import model.Member;

@FunctionalInterface
public interface MemberChangeListener {
    MemberChangeListener NO_OP = (change, member) -> {
    };

    enum Change {
        ADDED,
        UPDATED,
        DEACTIVATED,
        REACTIVATED,
        DELETED
    }

    void onMemberChanged(Change change, Member member);
}