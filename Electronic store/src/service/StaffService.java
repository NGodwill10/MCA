//  Class Description:
/*
    This class represents a staff service in the store.
    It contains methods to add a new staff to the store's
    records. 
    addStaff() takes the staff's name, address, email, and salary as input and creates an object of class Staff.
    It auto generates the id of the staff by calling the 
    getNextId() method of Staff class.
 */

package service;

import model.Staff;

public class StaffService {
    public StaffService() {

        int input;

        do {
            System.out.println("\n--- Staff Management ---");
            System.out.println("1. Add Staff");
            System.out.println("2. Remove Staff");
            System.out.println("3. Display Staff details by id");
            System.out.println("4. Display all Staff");
            System.out.println("0. Back to Main Menu");

            input = new java.util.Scanner(System.in).nextInt();
            new java.util.Scanner(System.in).nextLine(); // Clear scanner buffer

            switch (input) {
                case 1:
                    int id = AutoId.getNextId("data/staff.txt");
                    System.out.print("Enter staff name: ");
                    String name = new java.util.Scanner(System.in).nextLine();
                    System.out.print("Enter staff address: ");
                    String address = new java.util.Scanner(System.in).nextLine();
                    System.out.print("Enter staff email: ");
                    String email = new java.util.Scanner(System.in).nextLine();
                    System.out.print("Enter staff salary: ");
                    float salary = new java.util.Scanner(System.in).nextFloat();

                    Staff staff = new Staff(id, name, address, email, salary);
                    staff.addStaff(staff);
                    break;

                case 2:
                    System.out.print("Enter staff ID to remove: ");
                    int removeId = new java.util.Scanner(System.in).nextInt();
                    Staff.removeStaffById(removeId);
                    break;

                case 3:
                    System.out.print("Enter staff ID to display details: ");
                    int displayId = new java.util.Scanner(System.in).nextInt();
                    Staff.display(displayId);
                    break;

                case 4:
                    Staff.display();
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
