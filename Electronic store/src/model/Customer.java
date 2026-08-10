package model;

import service.FileService;

public class Customer extends Person {

    public Customer(int id, String name, String address, String email) {
        super(id, name, address, email);
    }

    public void addCustomer(Customer customer) {
        try {
            // Format the customer details as a string
            String customerData = customer.getId() + "," + customer.getName() + "," + customer.getAddress() + ","
                    + customer.getEmail();

            // Append the customer data to the customers.txt file in the data folder
            FileService.appendData("data/customers.txt", customerData);

            System.out.println("Customer added successfully!");
        } catch (Exception e) {
            System.err.println("Failed to add customer: " + e.getMessage());
        }
    }

    public static void removeCustomerById(int id) {
        try {
            FileService.removeLineById("data/customers.txt", id);
            System.out.println("Customer removed successfully!");
        } catch (Exception e) {
            System.err.println("Failed to remove customer: " + e.getMessage());
        }
    }

    public static void display() {
        // display details of all customers
        try {
            System.out.println("\n--- Customer Details ---");
            System.out.printf("%-5s %-20s %-30s %-30s%n", "ID", "Name", "Address", "Email");
            System.out.println("-------------------------------------------------------------------------------");

            for (String line : FileService.readAllLines("data/customers.txt")) {
                String[] parts = line.split(",");
                if (parts.length == 4) {
                    int id = Integer.parseInt(parts[0]);
                    String name = parts[1];
                    String address = parts[2];
                    String email = parts[3];

                    System.out.printf("%-5d %-20s %-30s %-30s%n", id, name, address, email);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to display customers: " + e.getMessage());
        }
    }

    // override display method to display details of a customer by id
    public static void display(int id) {
        try {
            // Read all lines from the customers.txt file
            java.util.List<String> lines = FileService.readAllLines("data/customers.txt");

            // Search for the customer with the given id
            for (String line : lines) {
                String[] parts = line.split(",");
                if (parts.length >= 4 && Integer.parseInt(parts[0]) == id) {
                    System.out.println("Customer Details:");
                    System.out.println("ID: " + parts[0]);
                    System.out.println("Name: " + parts[1]);
                    System.out.println("Address: " + parts[2]);
                    System.out.println("Email: " + parts[3]);
                    return;
                }
            }
            System.out.println("No customer found with ID: " + id);
        } catch (Exception e) {
            System.err.println("Failed to display customer: " + e.getMessage());
        }
    }
}