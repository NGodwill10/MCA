package model;

import service.FileService;
import service.AutoId;

public class Owner extends Person implements Stakeholder {
    float stake; // percentage of the store owned
    double investment; // amount of capital invested in the store

    public Owner(String name, String email, float stake, double investment) {
        super(AutoId.getNextId("data/stakeholders.txt"), name, email);
        this.stake = stake;
        this.investment = investment;
    }

    // Method to add an owner to the owners.txt file
    public void addOwner(Owner owner) {
        try {
            // Format the owner details as a string
            String ownerData = "owner," + owner.getId() + "," + owner.getName() + "," + owner.getEmail() + ","
                    + owner.stake + ","
                    + owner.investment;

            // Append the owner data to the owners.txt file in the data folder
            FileService.appendData("data/stakeholders.txt", ownerData);

            System.out.println("Owner added successfully!");
        } catch (Exception e) {
            System.err.println("Failed to add owner: " + e.getMessage());
        }
    }

}