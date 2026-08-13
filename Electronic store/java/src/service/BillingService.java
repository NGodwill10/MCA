//  Class description:
/*
    This class implements the Bill interface and provides functionality for billing in the electronic store system.
    It allows adding products to a bill, calculating the total amount, and storing the bill details in a file.

    methods:
    - getItemById(int id): Retrieves a product by its ID from the products.txt file.
    - addItem(Product product): Adds a product to the bill's item list.
    - calculateTotal(): Calculates the total amount of the bill and displays the bill details.
 */

package service;

import model.Bill;
import model.Product;

import java.util.List;
import java.util.Scanner;

public class BillingService implements Bill {
    private int itemCount = 0; // To keep track of the number of items in the itemList
    private int billId; // Auto-generated bill ID
    private final int customerId; // Customer ID for the bill
    private final String billFilePath = "data/bills.txt"; // File to store bills
    int input; // Variable to store user input for product ID

    public static float total;
    static Product product = itemList[50];

    Scanner sc = new Scanner(System.in);

    public BillingService(int customerId) {
        this.customerId = customerId;
        this.billId = AutoId.getNextId(billFilePath); // Auto-generate bill ID

        do {
            System.out
                    .println("\nInsert the product ID to add to the bill\n\t-1 to finish\n\t0 to calculate total bill");
            input = sc.nextInt();

            if (input != -1) {
                addItem(getItemById(input));
            }

            if (input == 0) {
                calculateTotal();
                displayBill(total);
            }
        } while (input != -1);

        // Store the bill in the file
        storeBill();
    }

    @Override
    public Product getItemById(int id) {
        try {
            // Read all lines from the products.txt file
            List<String> lines = FileService.readAllLines("data/products.txt");

            // Iterate through each line to find the product with the given ID
            for (String line : lines) {
                String[] fields = line.split(","); // Split the line into fields
                int productId = Integer.parseInt(fields[0]); // Parse the product ID

                if (productId == id) {
                    // Create a Product object using the fields
                    String name = fields[1];
                    String category = fields[2];
                    float price = Float.parseFloat(fields[3]);

                    Product product = new Product(id, name, category, price);
                    System.out.println("Product found: ");
                    Product.display(id); // Display the product details
                    return product; // Return the product
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading product data: " + e.getMessage());
        }

        System.out.println("Item with ID " + id + " not found.");
        return null; // Return null if the product is not found
    }

    @Override
    public void addItem(Product product) {
        if (product == null) {
            System.out.println("Cannot add a null product.");
            return;
        }

        if (itemCount < itemList.length) {
            itemList[itemCount++] = product;
            System.out.println("Product added successfully!");
        } else {
            System.out.println("Item list is full. Cannot add more products.");
        }
    }

    @Override
    public float calculateTotal() {
        total = 0;
        for (int i = 0; i < itemCount; i++) {
            total += itemList[i].getPrice();
        }
        return total;
    }

    public void displayBill(float total) {
        System.out.println("\n==========================================================");
        System.out.println("\n\t--- Bill Details ---");

        for (int i = 0; i < itemCount; i++) {
            System.out.println("\n" + (i + 1) + ". ");
            itemList[i].display();
            System.out.println("\n\t--------------------");
        }
        System.out.println("\n\t--------------------");
        System.out.println("\n\t\t\t\t\tTotal Amount: " + total);
        System.out.println("\n==========================================================");
    }

    private void storeBill() {
        try {
            System.out.println("Storing the bill in the file...");
            StringBuilder billData = new StringBuilder();
            billData.append(billId).append(",").append(customerId).append(",");

            for (int i = 0; i < itemCount; i++) {
                itemList[i];
                billData.append(product.getId()).append(",")
                        .append(product.getName()).append(",")
                        .append(product.getCategory()).append(",")
                        .append(product.getPrice()).append("\n");
            }

            FileService.appendData(billFilePath, billData.toString());
            System.out.println("Bill stored successfully!");
        } catch (Exception e) {
            System.err.println("Failed to store the bill: " + e.getMessage());
        }
    }
}