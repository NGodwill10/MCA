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

public class CustomerService {
    String name;
    String address;
    String email;
    int id;

    public CustomerService() {
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

            input = new java.util.Scanner(System.in).nextInt();
            new java.util.Scanner(System.in).nextLine(); // Clear scanner buffer

            switch (input) {
                case 1:
                    int id = AutoId.getNextId("data/customers.txt");
                    System.out.print("Enter customer name: ");
                    name = new java.util.Scanner(System.in).nextLine();
                    System.out.print("Enter customer address: ");
                    address = new java.util.Scanner(System.in).nextLine();
                    System.out.print("Enter customer email: ");
                    email = new java.util.Scanner(System.in).nextLine();

                    Customer customer = new Customer(id, name, address, email);
                    customer.addCustomer(customer);
                    break;

                case 2:
                    System.out.print("Enter customer ID to remove: ");
                    int removeId = new java.util.Scanner(System.in).nextInt();
                    Customer.removeCustomerById(removeId);
                    break;

                case 3:
                    System.out.print("Enter customer ID to display details: ");
                    int displayId = new java.util.Scanner(System.in).nextInt();
                    Customer.display(displayId);
                    break;

                case 4:
                    Customer.display();
                    break;

                case 5:
                    System.out.print("Enter customer ID to generate bill: ");
                    int customerId = new java.util.Scanner(System.in).nextInt();
                    new java.util.Scanner(System.in).nextLine(); // Clear scanner buffer
                    BillingService newBill = new BillingService(customerId);
                    break;

                case 6:
                    System.out.println("\n--- Product Repair Request ---");
                    System.out.println("Enter Product ID to repair:");
                    int repairProductId = new java.util.Scanner(System.in).nextInt();
                    System.out.print("Enter customer ID:");
                    int repairCustomerId = new java.util.Scanner(System.in).nextInt();
                    System.out.println("Enter staff ID to assign repair to:");
                    int repairStaffId = new java.util.Scanner(System.in).nextInt();
                    new java.util.Scanner(System.in).nextLine(); // Clear scanner buffer
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
