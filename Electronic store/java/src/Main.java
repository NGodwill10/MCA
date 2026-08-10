//  Author   :   Yeshwant Shetgaonkar
//  Roll No. :   2661

/*  Description :

    This program represents the working of the actual electronic store system. 

    Please note that this program doesn't actully perform the end functionalities rather just shows the hierarchy and working of the Electronic store management system.
    Some basec implementation of the following is made:
        - Object Oriented Programming
        - Abstraction
        - Encapsulation
        - Inheritance
        - Overriding
        - Overloading
        - File handling (This I added extra)
 */

import java.util.Scanner;
import model.Customer;
import model.Investor;
import model.Owner;
import model.Product;
import model.Staff;
import model.Supplier;
import model.Warehouse;
import service.*;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        int input;

        do {
            System.out.println("\n--- Electronic Store Management ---");
            System.out.println("1. Product Management");
            System.out.println("2. Customer Management");
            System.out.println("3. Staff Management");
            System.out.println("4. Stakeholder Management");
            System.out.println("5. Inventory Management");

            input = sc.nextInt();
            sc.nextLine(); // Clear scanner buffer

            switch (input) {
                case 1:
                    ProductService productService = new ProductService();
                    break;

                case 2:
                    CustomerService customerService = new CustomerService();
                    break;

                case 3:
                    StaffService staffService = new StaffService();
                    break;

                case 4:
                    StakeholderService stakeholderService = new StakeholderService();
                    break;

                case 5:
                    InventoryService inventoryService = new InventoryService();
                    break;

                case 0:
                    System.out.println("\nExiting...");
                    break;

                default:
                    System.out.println("\nInvalid choice. Please try again.");
            }
        } while (input != 0);

        sc.close();
    }
}