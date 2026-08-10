package model;

import service.FileService;

import java.util.List;

public class Warehouse {

    // This method is used to order a product from a supplier.
    public static void orderProduct(int productId, int supplierId, int quantity) {
        try {
            // Get the name of the supplier with the corresponding ID from suppliers.txt
            List<String> supplierLines = FileService.readAllLines("data/suppliers.txt");
            String supplierName = null;
            String supplierEmail = null;

            for (String line : supplierLines) {
                String[] fields = line.split(","); // Split the line into fields
                int id = Integer.parseInt(fields[0]); // Parse the supplier ID

                if (id == supplierId) {
                    supplierName = fields[1]; // Get the supplier name
                    supplierEmail = fields[3]; // Get the supplier email
                    break;
                }
            }

            if (supplierName == null) {
                System.out.println("Supplier with ID " + supplierId + " not found.");
                return;
            }

            // Get the name of the product with the corresponding ID from products.txt
            List<String> productLines = FileService.readAllLines("data/products.txt");
            String productName = null;

            for (String line : productLines) {
                String[] fields = line.split(","); // Split the line into fields
                int id = Integer.parseInt(fields[0]); // Parse the product ID

                if (id == productId) {
                    productName = fields[1]; // Get the product name
                    break;
                }
            }

            if (productName == null) {
                System.out.println("Product with ID " + productId + " not found.");
                return;
            }

            // Print the order details
            System.out.println("Product " + productName + " with ID: " + productId +
                    " is being ordered from Supplier: " + supplierName);

            // Call the Supplier's orderProduct method (assuming it exists)
            Supplier supplier = new Supplier(productId, quantity, supplierName, supplierEmail);
            supplier.orderProduct(productId, quantity);

        } catch (Exception e) {
            System.err.println("Error processing order: " + e.getMessage());
        }
    }

    public static void loadProduct(int id, int quantity) {
        /*
         * Logic for loading products into the warehouse
         * 
         */
    }

    public static void requestProduct(int productId, int quantity) {
        try {
            // Read all lines from stocks.txt
            List<String> stockLines = FileService.readAllLines("data/stocks.txt");
            boolean productFound = false;

            for (int i = 0; i < stockLines.size(); i++) {
                String[] fields = stockLines.get(i).split(","); // Split the line into fields
                int id = Integer.parseInt(fields[0]); // Parse the product ID

                if (id == productId) {
                    productFound = true;
                    int quantityInStore = Integer.parseInt(fields[1]);
                    int quantityInWarehouse = Integer.parseInt(fields[2]);

                    if (quantityInWarehouse >= quantity) {
                        // Update the quantities
                        quantityInStore += quantity;
                        quantityInWarehouse -= quantity;

                        // Update the line in the file
                        stockLines.set(i, productId + "," + quantityInStore + "," + quantityInWarehouse);
                        FileService.writeAllLines("data/stocks.txt", stockLines);

                        System.out.println("Product successfully added to the store. Updated quantities:");
                        System.out.println("Quantity in Store: " + quantityInStore);
                        System.out.println("Quantity in Warehouse: " + quantityInWarehouse);
                    } else {
                        // Not enough stock in the warehouse, order more
                        System.out.println("Not enough stock in the warehouse. Ordering more...");
                        int requiredQuantity = quantity - quantityInWarehouse;

                        // Call orderProduct to order more stock
                        System.out.println("Enter Supplier Id to order from:");
                        int supplierId = new java.util.Scanner(System.in).nextInt();
                        orderProduct(productId, supplierId, requiredQuantity);

                        // Update the quantities after the order
                        quantityInStore += quantity;
                        quantityInWarehouse = 0;

                        // Update the line in the file
                        stockLines.set(i, productId + "," + quantityInStore + "," + quantityInWarehouse);
                        FileService.writeAllLines("data/stocks.txt", stockLines);

                        System.out
                                .println("Product successfully added to the store after ordering. Updated quantities:");
                        System.out.println("Quantity in Store: " + quantityInStore);
                        System.out.println("Quantity in Warehouse: " + quantityInWarehouse);
                    }
                    break;
                }
            }

            if (!productFound) {
                System.out.println("Product with ID " + productId + " not found in stocks.txt.");
            }
        } catch (Exception e) {
            System.err.println("Error processing product request: " + e.getMessage());
        }
    }
}