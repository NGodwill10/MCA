//  Class description:
/*
    This class represents a product in the electronic store system.
    It contains attributes such as id, name, category, and price.

    It provides methods to add, remove, and display product details.
 */

package model;

import service.FileService;

public class Product {
    final int id;
    final String name;
    final String category;

    float price; // Price keeps changing

    public Product(int id, String name, String category, float price) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
    }

    public void addProduct(Product product) {
        try {
            // Format the product details as a string
            String productData = product.getId() + "," + product.getName() + "," + product.getCategory() + ","
                    + product.getPrice();

            // Append the product data to the products.txt file in the data folder
            FileService.appendData("data/products.txt", productData);

            System.out.println("Product added successfully!");
        } catch (Exception e) {
            System.err.println("Failed to add Prodyct: " + e.getMessage());
        }

    }

    public void removeProductById(int id) {
        try {
            FileService.removeLineById("data/products.txt", id);
            System.out.println("Product removed successfully!");
        } catch (Exception e) {
            System.err.println("Failed to remove product: " + e.getMessage());
        }
    }

    public void setPrice(float price) {
        this.price = price;
    }

    public float getPrice() {
        return price;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public static void display(int id) {
        try {
            // Read all lines from the products.txt file
            java.util.List<String> lines = FileService.readAllLines("data/products.txt");

            // Search for the product with the given id
            for (String line : lines) {
                String[] parts = line.split(",");
                if (parts.length >= 4 && Integer.parseInt(parts[0]) == id) {
                    System.out.println("Product Details:");
                    System.out.println("ID: " + parts[0]);
                    System.out.println("Name: " + parts[1]);
                    System.out.println("Category: " + parts[2]);
                    System.out.println("Price: " + parts[3]);
                    return;
                }
            }

            System.out.println("Product with ID " + id + " not found.");
        } catch (Exception e) {
            System.err.println("Failed to display product details: " + e.getMessage());
        }
    }

    // overloaded display method to show all products
    public static void display() {
        try {
            // Read all lines from the products.txt file
            java.util.List<String> lines = FileService.readAllLines("data/products.txt");

            System.out.println("Product List:");
            for (String line : lines) {
                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    System.out.println("ID: " + parts[0] + ", Name: " + parts[1] + ", Category: " + parts[2]
                            + ", Price: " + parts[3]);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to display product list: " + e.getMessage());
        }
    }
}
