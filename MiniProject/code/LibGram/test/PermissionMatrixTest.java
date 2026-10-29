import model.User;
import services.PermissionPolicy;

public final class PermissionMatrixTest {
    private PermissionMatrixTest() { }

    public static void main(String[] args) {
        PermissionPolicy policy = new PermissionPolicy();
        User admin = TestSupport.user(User.Role.ADMIN, true);
        User staff = TestSupport.user(User.Role.STAFF, true);
        User inactive = TestSupport.user(User.Role.ADMIN, false);
        TestSupport.check(policy.canManageCatalog(admin) && policy.canManageMembers(admin)
                && policy.canOpenSettings(admin) && policy.canManageAccounts(admin) && policy.canCirculate(admin),
                "Admin permission matrix");
        TestSupport.check(!policy.canManageCatalog(staff) && !policy.canManageMembers(staff)
                && !policy.canOpenSettings(staff) && !policy.canManageAccounts(staff)
                && policy.canViewMembers(staff) && policy.canCirculate(staff), "Staff permission matrix");
        TestSupport.check(!policy.canManageCatalog(inactive), "Inactive user cannot manage catalog through service boundary");
        System.out.println("Permission matrix tests passed.");
    }
}
