//  Class description:
/*
    This class represents a person with an id, name, address, and email.
    The id and name are final and cannot be changed after the object is created.
    The address and email can be changed using the setAddress and setEmail methods.
    The display method prints the person details to the console.
*/

package model;

import service.AutoId;

class Person {

    final int id;
    final String name;
    String address;
    String email;

    public Person(int id, String name, String address, String email) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.email = email;
    }

    public Person(int id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;

    }

    // constructor overloading for people like supplier doesn't have to provide
    // their address
    public Person(String name, String email) {
        this.id = AutoId.getNextId("data/stakeholders.txt");
        this.name = name;
        this.email = email;
    }

    public int getId() {
        return id;
    }

    public String getAddress() {
        return address;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }
}