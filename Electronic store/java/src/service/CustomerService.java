//  Class Description:
/*
    This class represents a customer service in the store.
    It contains methods to add a new customer to the store's
    records. 
    addCustomer() takes the customer's name, address, 
    and email as input and creates an object of class Customer.
    and calls the addCustomer() method of Customer class.
    It auto generates the id of the customer by calling the 
    getNextId() method of Customer class.
*/

package service;

import model.Bill;
import model.Customer;
import java.util.Scanner;

public class CustomerService {
    String name;
    String address;
    String email;
    int id;

    public CustomerService(Scanner scanner) {
        int input;
        do {
            System.out.println("\n--- Customer Management ---");
            System.out.println("1. Add Customer");
            System.out.println("2. Remove Customer");
            System.out.println("3. Display Customer details by id");
            System.out.println("4. Display all Customers");
            System.out.println("5. Generate Bill");
            System.out.println("6. Request Product Repair");
            System.out.println("0. Back to Main Menu");

            input = scanner.nextInt();
            scanner.nextLine();

            switch (input) {
                case 1:
                    int id = AutoId.getNextId("data/customers.txt");
                    System.out.print("Enter customer name: ");
                    name = scanner.nextLine();
                    System.out.print("Enter customer address: ");
                    address = scanner.nextLine();
                    System.out.print("Enter customer email: ");
                    email = scanner.nextLine();

                    Customer customer = new Customer(id, name, address, email);
                    customer.addCustomer(customer);
                    break;

                case 2:
                    System.out.print("Enter customer ID to remove: ");
                    int removeId = scanner.nextInt();
                    Customer.removeCustomerById(removeId);
                    break;

                case 3:
                    System.out.print("Enter customer ID to display details: ");
                    int displayId = scanner.nextInt();
                    Customer.display(displayId);
                    break;

                case 4:
                    Customer.display();
                    break;

                case 5:
                    System.out.print("Enter customer ID to generate bill: ");
                    int customerId = scanner.nextInt();
                    scanner.nextLine();
                    BillingService newBill = new BillingService(customerId, scanner);
                    break;

                case 6:
                    System.out.println("\n--- Product Repair Request ---");
                    System.out.println("Enter Product ID to repair:");
                    int repairProductId = scanner.nextInt();
                    System.out.print("Enter customer ID:");
                    int repairCustomerId = scanner.nextInt();
                    System.out.println("Enter staff ID to assign repair to:");
                    int repairStaffId = scanner.nextInt();
                    scanner.nextLine();
                    RepairService repairService = new RepairService();
                    repairService.requestRepair(repairProductId, repairCustomerId, repairStaffId);
                    break;

                case 0:
                    System.out.println("\nReturning to Main Menu...");
                    break;

                default:
                    System.out.println("\nInvalid choice. Please try again.");
            }
        } while (input != 0);
    }
}
