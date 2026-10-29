package data;

import java.io.IOException;
import java.util.List;
import model.Contact;

public interface ContactRepository {
    List<Contact> findAll();

    void saveAll(List<Contact> contacts) throws IOException;

    static ContactRepository empty() {
        return new ContactRepository() {
            @Override
            public List<Contact> findAll() {
                return List.of();
            }

            @Override
            public void saveAll(List<Contact> contacts) {
                throw new UnsupportedOperationException("This repository is read-only and empty.");
            }
        };
    }
}