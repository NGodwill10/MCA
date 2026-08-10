package service;

import model.Staff;

public class RepairService implements Runnable {

    private int customerId;
    private int productId;
    private int staffId;

    public void requestRepair(int productId, int customerId, int StaffId) {
        this.customerId = customerId;
        this.productId = productId;
        this.staffId = StaffId;

        // Create and start a new thread
        Thread repairThread = new Thread(this);
        repairThread.start();
    }

    @Override
    public void run() {
        try {
            // Generate a unique repair ID using AutoId
            String repairFilePath = "data/repairs.txt";
            int repairId = AutoId.getNextId(repairFilePath);

            // Format the repair request details as a string
            String repairData = repairId + "," + customerId + "," + productId + "," + staffId;

            // Append the repair request to the repairs.txt file
            FileService.appendData(repairFilePath, repairData);

            // Simulate repair processing time
            System.out
                    .println("Processing repair request for Customer ID: " + customerId + ", Product ID: " + productId);
            Thread.sleep(5000); // Wait for 5000 milliseconds (5 seconds)

            // Print success message
            System.out.println("Repair successful! Repair ID: " + repairId);
        } catch (Exception e) {
            System.err.println("Failed to process repair request: " + e.getMessage());
        }
    }
}