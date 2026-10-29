package services;

import data.MemberRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import model.Member;
import model.Charge;
import model.User;

public final class MembershipService {
    private final MemberRepository members;
    private final ChargeService charges;
    private final SettingsService settings;
    private final PermissionPolicy permissions;

    public MembershipService(MemberRepository members, ChargeService charges, SettingsService settings) {
        this.members = members;
        this.charges = charges;
        this.settings = settings;
        this.permissions = new PermissionPolicy();
    }

    public Member renew(User actor, String memberId) throws IOException {
        if (actor == null || !actor.active() || !permissions.canManageMembers(actor))
            throw new SecurityException("Only an administrator can renew membership.");
        Member member = members.findAll().stream().filter(m -> m.memberId().equals(memberId)).findFirst().orElseThrow();
        if (charges.forMember(memberId).stream()
                .anyMatch(c -> c.type() != model.ChargeType.MEMBERSHIP_RENEWAL && c.balance().signum() > 0))
            throw new IllegalArgumentException("Membership renewal requires all non-renewal dues to be paid.");
        LocalDate start = LocalDate.now().isAfter(member.expiryDate()) ? LocalDate.now() : member.expiryDate();
        Member renewed = new Member(member.memberId(), member.name(), member.phone(), member.email(), member.address(),
                member.membershipType(), member.joinDate(),
                start.plusDays(
                        settings.getInt(SettingsDefaults.key("membership.lengthDays.", member.membershipType()))),
                member.status(), member.createdAt(), LocalDateTime.now());
        List<Member> oldMembers = members.findAll();
        ArrayList<Member> rows = new ArrayList<>(oldMembers);
        rows.set(rows.indexOf(member), renewed);
        List<Charge> oldCharges = charges.allRecords();
        List<Charge> nextCharges = new ArrayList<>(oldCharges);
        var fee = settings.getMoney(SettingsDefaults.key("fee.membership.annual.", member.membershipType()));
        if (fee.signum() > 0) {
            nextCharges.add(charges.create(memberId, "", model.ChargeType.MEMBERSHIP_RENEWAL,
                    "Membership renewal", fee));
        }
        try {
            members.saveAll(rows);
            charges.replaceAll(nextCharges);
        } catch (IOException | RuntimeException exception) {
            try {
                members.saveAll(oldMembers);
            } catch (IOException rollback) {
                exception.addSuppressed(rollback);
            }
            try {
                charges.replaceAll(oldCharges);
            } catch (IOException rollback) {
                exception.addSuppressed(rollback);
            }
            throw exception;
        }
        return renewed;
    }
}
