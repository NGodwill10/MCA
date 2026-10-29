package services;

import java.util.LinkedHashMap;
import java.util.Map;
import model.MembershipType;

/** The one authoritative set of starting values for LibGram settings. */
public final class SettingsDefaults {
    private SettingsDefaults() {
    }

    public static Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("schemaVersion", "1");
        values.put("alerts.enabled", "true");
        values.put("alerts.membershipExpiryDays", "14");
        values.put("charge.base.enabled", "false");
        values.put("charge.base.amount", "0.00");
        values.put("charge.rent.enabled", "false");
        values.put("charge.rent.unit", "DAY");
        values.put("charge.rent.amount", "0.00");
        values.put("charge.penalty.enabled", "true");
        values.put("charge.penalty.perDay", "2.00");
        values.put("charge.penalty.maxPerItem", "100.00");
        values.put("loan.defaultDays", "14");
        values.put("loan.maxDays", "90");
        values.put("lost.afterDays", "60");
        values.put("refund.windowDays", "180");
        values.put("fee.lostProcessing", "50.00");
        values.put("fee.damagedProcessing", "25.00");
        values.put("fee.cardReplacement", "50.00");
        values.put("fee.reservation", "0.00");
        values.put("reservation.holdDays", "3");
        values.put("reservation.maxPerMember", "3");
        values.put("stock.lowThreshold", "1");
        values.put("limit.INDIVIDUAL", "3");
        values.put("limit.FAMILY", "6");
        values.put("limit.SENIOR", "3");
        values.put("limit.TEMPORARY", "2");
        values.put("membership.lengthDays.INDIVIDUAL", "365");
        values.put("membership.lengthDays.FAMILY", "365");
        values.put("membership.lengthDays.SENIOR", "365");
        values.put("membership.lengthDays.TEMPORARY", "30");
        values.put("fee.membership.registration.INDIVIDUAL", "100.00");
        values.put("fee.membership.registration.FAMILY", "200.00");
        values.put("fee.membership.registration.SENIOR", "0.00");
        values.put("fee.membership.registration.TEMPORARY", "50.00");
        values.put("fee.membership.annual.INDIVIDUAL", "100.00");
        values.put("fee.membership.annual.FAMILY", "200.00");
        values.put("fee.membership.annual.SENIOR", "0.00");
        values.put("fee.membership.annual.TEMPORARY", "0.00");
        values.put("membership.label.INDIVIDUAL", "Individual");
        values.put("membership.label.FAMILY", "Family");
        values.put("membership.label.SENIOR", "Senior");
        values.put("membership.label.TEMPORARY", "Temporary");
        return values;
    }

    public static String key(String prefix, MembershipType type) {
        return prefix + type.name();
    }
}
