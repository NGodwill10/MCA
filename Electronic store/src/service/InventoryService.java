package service;

import model.Warehouse;
import model.Supplier;

public class InventoryService {

    public InventoryService() {
        int input;

        do {
            System.out.println("--- Inventory Management ---");
            System.out.println("1. Request Product from Warehouse");
            System.out.println("2. Add Supplier");
            System.out.println("0. Exit to Main Menu");
            input = new java.util.Scanner(System.in).nextInt();
            // Clear scanner buffer
            new java.util.Scanner(System.in).nextLine();

            switch (input) {
                case 1:
                    System.out.println("insert product id");
                    int productId = new java.util.Scanner(System.in).nextInt();
                    System.out.println("insert quantity");
                    int quantity = new java.util.Scanner(System.in).nextInt();
                    Warehouse.requestProduct(productId, quantity);
                    break;

                case 2:
                    System.out.println("insert supplier name");
                    String name = new java.util.Scanner(System.in).nextLine();
                    System.out.println("insert supplier email");
                    String email = new java.util.Scanner(System.in).nextLine();
                    Supplier supplier = new Supplier(name, email);
                    supplier.addSupplier();
                    break;

                default:
                    break;
            }
        } while (input != 0);

    }
}
