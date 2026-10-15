package service;

import model.Warehouse;
import model.Supplier;
import java.util.Scanner;

public class InventoryService {

    public InventoryService(Scanner scanner) {
        int input;

        do {
            System.out.println("--- Inventory Management ---");
            System.out.println("1. Request Product from Warehouse");
            System.out.println("2. Add Supplier");
            System.out.println("0. Exit to Main Menu");
            input = scanner.nextInt();
            scanner.nextLine();

            switch (input) {
                case 1:
                    System.out.println("insert product id");
                    int productId = scanner.nextInt();
                    System.out.println("insert quantity");
                    int quantity = scanner.nextInt();
                    Warehouse.requestProduct(productId, quantity, scanner);
                    break;

                case 2:
                    System.out.println("insert supplier name");
                    String name = scanner.nextLine();
                    System.out.println("insert supplier email");
                    String email = scanner.nextLine();
                    Supplier supplier = new Supplier(name, email);
                    supplier.addSupplier();
                    break;

                default:
                    break;
            }
        } while (input != 0);

    }
}
