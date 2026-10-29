package services;

import model.User;
import model.User.Role;

public final class PermissionPolicy {
    public boolean canManageCatalog(User user) {
        return active(user) && user.role() == Role.ADMIN;
    }

    public boolean canManageMembers(User user) {
        return active(user) && user.role() == Role.ADMIN;
    }

    public boolean canViewMembers(User user) {
        return active(user) && (user.role() == Role.ADMIN || user.role() == Role.STAFF);
    }

    public boolean canOpenSettings(User user) {
        return active(user) && user.role() == Role.ADMIN;
    }

    public boolean canManageAccounts(User user) {
        return active(user) && user.role() == Role.ADMIN;
    }

    public boolean canCirculate(User user) {
        return active(user) && (user.role() == Role.ADMIN || user.role() == Role.STAFF);
    }

    private static boolean active(User user) {
        return user != null && user.active();
    }
}
