//  Class Description:
/*
    This class represents the service layer for managing products in the electronic store system.
    It provides methods to add, remove, and update products.
    The ProductService class interacts with the Product model and handles user input for product management.
    It also utilizes the AutoId class to generate unique IDs for new products.
 */

package service;

import model.Product;

public class ProductService {

    public ProductService() {
        int input;
        do {
            System.out.println("\n--- Product Management ---");
            System.out.println("1. Add Product");
            System.out.println("2. Remove Product");
            System.out.println("3. Update Product Price");
            System.out.println("4. Display Product details by id");
            System.out.println("5. Display all Products");
            System.out.println("0. Back to Main Menu");

            input = new java.util.Scanner(System.in).nextInt();
            new java.util.Scanner(System.in).nextLine(); // Clear scanner buffer

            switch (input) {
                case 1:
                    System.out.print("Enter product name: ");
                    String name = new java.util.Scanner(System.in).nextLine();
                    System.out.print("Enter product category: ");
                    String category = new java.util.Scanner(System.in).nextLine();
                    System.out.print("Enter product price: ");
                    float price = new java.util.Scanner(System.in).nextFloat();
                    int id = AutoId.getNextId("data/products.txt");
                    Product product = new Product(id, name, category, price);
                    product.addProduct(product);
                    break;

                case 2:
                    System.out.print("Enter product ID to remove: ");
                    int removeId = new java.util.Scanner(System.in).nextInt();
                    Product productToRemove = new Product(removeId, "", "", 0);
                    productToRemove.removeProductById(removeId);
                    break;

                case 3:
                    System.out.print("Enter product ID to update price: ");
                    int updateId = new java.util.Scanner(System.in).nextInt();
                    System.out.print("Enter new price: ");
                    float newPrice = new java.util.Scanner(System.in).nextFloat();
                    Product productToUpdate = new Product(updateId, "", "", 0);
                    productToUpdate.setPrice(newPrice);
                    break;

                case 4:
                    System.out.print("Enter product ID to display details: ");
                    int displayId = new java.util.Scanner(System.in).nextInt();
                    Product.display(displayId);
                    break;

                case 5:
                    Product.display();
                    break;

                case 0:
                    System.out.println("\nReturning to Main Menu...");
                    break;

                default:
                    System.out.println("\nInvalid choice. Please try again.");
            }
        } while (input != 0);
    }

    void addProduct(String name, String category, float price) {
        int id = AutoId.getNextId("data/products.txt");
        Product product = new Product(id, name, category, price);
        product.addProduct(product);

    }
}