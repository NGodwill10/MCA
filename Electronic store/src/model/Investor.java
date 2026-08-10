//  cladd description:
/*
    This class represents an investor, which is a type of person who owns a stake in a store.
    It extends the Person class and implements the Stakeholder interface.
    The Investor class has additional attributes for stake (percentage of ownership) and investment (amount of capital invested).
    It provides methods to add an investor to a file, display investor details, and get/set the stake and investment values.
 */

package model;

import service.FileService;

public class Investor extends Person implements Stakeholder {
    private float stake; // percentage of the store owned
    private double investment; // amount of capital invested in the store

    public Investor(int id, String name, String email, float stake, double investment) {
        super(name, email);
        this.stake = stake;
        this.investment = investment;
    }

    // Method to add an investor to the investors.txt file
    public void addInvestor(Investor investor) {
        try {
            // Format the investor details as a string
            String investorData = "investor," + investor.getId() + "," + investor.getName() + "," + investor.getEmail()
                    + ","
                    + investor.stake + ","
                    + investor.investment;

            // Append the investor data to the investors.txt file in the data folder
            FileService.appendData("data/stakeholders.txt", investorData);

            System.out.println("Investor added successfully!");
        } catch (Exception e) {
            System.err.println("Failed to add investor: " + e.getMessage());
        }
    }

    public float getStake() {
        return stake;
    }

    public void setStake(float stake) {
        this.stake = stake;
    }

    public double getInvestment() {
        return investment;
    }

    public void setInvestment(double investment) {
        this.investment = investment;
    }

    void display() {
        System.out.println("\nThis person is an Investor");
        System.out.println("\nId: " + getId());
        System.out.println("\nName: " + getName());
        System.out.println("\nEmail: " + getEmail());
        System.out.println("\nStake: " + stake + "%");
        System.out.println("\nInvestment: $" + investment);
    }

}
