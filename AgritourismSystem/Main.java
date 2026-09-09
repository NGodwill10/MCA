import java.util.Scanner;

/**
 * <b>Agri-gate-tour</b>
 * <p>
 * <i>One stop Agritourism solution for farmers and tourists.</i>
 * </p>
 * 
 * Menu-driven entry point to demonstrate all inheritance types.
 * 
 * @author Yeshwant
 * @version 1.0
 * @since 2026-08-25
 */
public class Main {

    /**
     * Private constructor to prevent default instantiation warnings in Javadoc.
     */
    private Main() {
        // Utility class constructor
    }

    /**
     * Main entry point for the menu-driven application.
     * 
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        AgriTourismPackage tourPackage = new AgriTourismPackage("Green Valley Organics", "Goa", "Spice Harvest",
                "Monsoon", 750.0, 20);
        FarmStay stay = new FarmStay("Green Valley Organics", "Goa", "Spice Harvest", "Monsoon", 5);

        int choice;
        do {
            System.out.println("\n========== AGRI-GATE-TOUR ==========\n=== Agritourism Management system ==\n");
            System.out.println("1. Display Crop Details");
            System.out.println("2. Display Full Agri-Tourism Package");
            System.out.println("3. Display Farm Stay Info");
            System.out.println("4. Book Tour & Calculate Export Tax");
            System.out.println("5. Exit");

            choice = scanner.nextInt();

            switch (choice) {
                case 1 -> {
                    System.out.println("\n--- Crop Details ---");
                    tourPackage.displayCropDetails();
                }
                case 2 -> {
                    System.out.println("\n--- Agritourism package ---");
                    tourPackage.displayCropDetails();
                }
                case 3 -> {
                    System.out.println("\n--- Farm stay ---");
                    stay.displayStayInfo();
                }
                case 4 -> {
                    System.out.println("Book Tour");
                    System.out.print("Enter number of slots to book: ");
                    int slots = scanner.nextInt();
                    tourPackage.bookPackage(slots);

                    System.out.print("Enter crop export quantity (tons): ");
                    double tons = scanner.nextDouble();
                    tourPackage.calculateExportTax(tons);
                }
                case 5 -> System.out.println("Exiting system...");
                default -> System.out.println("Invalid choice!");
            }
        } while (choice != 5);

        scanner.close();
    }
}