//  Class description:
/*
    This class represents a staff member, which is a type of person with an additional salary attribute.
    It extends the Person class and provides methods to manage staff members, including removing staff by ID
    and displaying staff details.

    Attributes:
    - salary: The salary of the staff member.

    Methods:
    - getSalary(): Returns the salary of the staff member.
    - removeStaffById(int id): Removes a staff member from the data file based on their ID.
    - display(): Displays all staff members' details from the data file.
    - display(int id): Displays the details of a specific staff member based on their ID.

*/
package model;

public class Staff extends Person {
    float salary;

    public Staff(int id, String name, String address, String email, float salary) {
        super(id, name, address, email);
        this.salary = salary;
    }

    public void addStaff(Staff staff) {
        try {
            // Format the staff details as a string
            String staffData = staff.getId() + "," + staff.getName() + "," + staff.getAddress() + ","
                    + staff.getEmail() + ","
                    + staff.salary;

            // Append the staff data to the staff.txt file in the data folder
            service.FileService.appendData("data/staff.txt", staffData);

            System.out.println("Staff added successfully!");
        } catch (Exception e) {
            System.err.println("Failed to add staff: " + e.getMessage());
        }
    }

    public float getSalary() {
        return salary;
    }

    public static void removeStaffById(int id) {
        try {
            service.FileService.removeLineById("data/staff.txt", id);
            System.out.println("Staff removed successfully!");
        } catch (Exception e) {
            System.err.println("Failed to remove staff: " + e.getMessage());
        }
    }

    public static void display() {
        // display all the staff members from the staff.txt file
        try {
            System.out.println("\n--- Staff Details ---");
            System.out.printf("%-5s %-20s %-30s %-30s %-10s%n", "ID", "Name", "Address", "Email", "Salary");
            System.out.println("-------------------------------------------------------------------------------");

            for (String line : service.FileService.readAllLines("data/staff.txt")) {
                String[] parts = line.split(",");
                if (parts.length == 5) {
                    int id = Integer.parseInt(parts[0]);
                    String name = parts[1];
                    String address = parts[2];
                    String email = parts[3];
                    float salary = Float.parseFloat(parts[4]);

                    System.out.printf("%-5d %-20s %-30s %-30s %-10.2f%n", id, name, address, email, salary);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to display staff: " + e.getMessage());
        }
    }

    // ovearloaded display method to display details of a staff member by id
    public static void display(int id) {
        try {
            // Read all lines from the staff.txt file
            java.util.List<String> lines = service.FileService.readAllLines("data/staff.txt");

            // Search for the staff member with the given id
            for (String line : lines) {
                String[] parts = line.split(",");
                if (parts.length >= 5 && Integer.parseInt(parts[0]) == id) {
                    System.out.println("Staff Details:");
                    System.out.println("ID: " + parts[0]);
                    System.out.println("Name: " + parts[1]);
                    System.out.println("Address: " + parts[2]);
                    System.out.println("Email: " + parts[3]);
                    System.out.println("Salary: " + parts[4]);
                    return;
                }
            }

            System.out.println("Staff with ID " + id + " not found.");
        } catch (Exception e) {
            System.err.println("Failed to display staff details: " + e.getMessage());
        }
    }

}
