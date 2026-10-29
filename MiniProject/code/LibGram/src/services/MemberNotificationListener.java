package services;

import java.io.IOException;
import model.Member;
import model.NotificationContext;
import model.NotificationType;

public final class MemberNotificationListener implements MemberChangeListener {
    private final NotificationService notifications;
    public MemberNotificationListener(NotificationService notifications) { this.notifications = notifications; }
    @Override public void onMemberChanged(Change change, Member member) {
        try {
            NotificationType type = switch (change) {
                case ADDED -> NotificationType.MEMBER_ADDED; case UPDATED, REACTIVATED -> NotificationType.MEMBER_UPDATED;
                case DEACTIVATED -> NotificationType.MEMBER_DEACTIVATED; case DELETED -> NotificationType.MEMBER_DELETED;
            };
            notifications.addEvent(NotificationContext.MEMBERS, type,
                    "Member " + change.name().toLowerCase() + ": " + member.name() + " (" + member.memberId() + ")",
                    member.memberId());
        } catch (IOException exception) { throw new IllegalStateException("Could not write member notification.", exception); }
    }
}
