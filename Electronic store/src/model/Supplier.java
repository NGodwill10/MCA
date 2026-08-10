package model;

import service.AutoId;

public class Supplier extends Person implements Runnable {

    int quantity, productId;

    public Supplier(String name, String email) {
        super(name, email);
    }

    // Constructor overloading for supplier to provide productId and quantity
    public Supplier(int productId, int quantity, String name, String email) {
        super(name, email); // Call to the Person constructor
        this.productId = productId;
        this.quantity = quantity;
    }

    // Method to add a supplier to the suppliers.txt file
    public void addSupplier() {
        try {
            int id = AutoId.getNextId("data/suppliers.txt");
            String supplierData = id + "," + getName() + "," + getEmail();
            service.FileService.appendData("data/suppliers.txt", supplierData);
            System.out.println("Supplier added successfully: " + supplierData);
        } catch (Exception e) {
            System.err.println("Error adding supplier: " + e.getMessage());
        }
    }

    // Method to order a product
    public void orderProduct(int productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;

        // Create and start a new thread
        Thread orderThread = new Thread(this);
        orderThread.start();
    }

    @Override
    public void run() {
        try {
            // Simulate a delay for the order processing
            System.out.println("Processing order for " + quantity + " units of product ID: " + productId +
                    " from supplier: " + getName());
            Thread.sleep(5000); // Wait for 5 seconds
            System.out.println("Order completed for " + quantity + " units of product ID: " + productId +
                    " from supplier: " + getName());
            Warehouse.loadProduct(productId, quantity); // Update stock after order completion
        } catch (InterruptedException e) {
            System.err.println("Order processing interrupted: " + e.getMessage());
        }
    }
}