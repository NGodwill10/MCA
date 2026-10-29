package services;

import model.Contact;

public final class DuplicateContactException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final transient Contact existingContact;
    private final String matchedField;

    public DuplicateContactException(Contact existingContact, String matchedField) {
        super(existingContact.name() + " already has this " + matchedField + ". Add anyway?");
        this.existingContact = existingContact;
        this.matchedField = matchedField;
    }

    public Contact existingContact() {
        return existingContact;
    }

    public String matchedField() {
        return matchedField;
    }
}