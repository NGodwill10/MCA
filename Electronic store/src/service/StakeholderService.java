package service;

import model.Investor;
import model.Owner;

public class StakeholderService {

    public StakeholderService() {
        int input;
        do {

            System.out.println("\n--- Stakeholder Management ---");
            System.out.println("1. Add Investor");
            System.out.println("2. Add Owner");
            System.out.println("0. Back to Main Menu");

            input = new java.util.Scanner(System.in).nextInt();
            new java.util.Scanner(System.in).nextLine(); // Clear scanner buffer

            switch (input) {
                case 1:
                    int id = AutoId.getNextId("data/stakeholders.txt");
                    System.out.print("Enter investor name: ");
                    String name = new java.util.Scanner(System.in).nextLine();
                    System.out.print("Enter investor email: ");
                    String email = new java.util.Scanner(System.in).nextLine();
                    System.out.print("Enter investor stake: ");
                    float stake = new java.util.Scanner(System.in).nextFloat();
                    System.out.print("Enter investor investment: ");
                    double investment = new java.util.Scanner(System.in).nextDouble();

                    Investor investor = new Investor(id, name, email, stake, investment);
                    investor.addInvestor(investor);
                    break;

                case 2:
                    System.out.print("Enter owner name: ");
                    name = new java.util.Scanner(System.in).nextLine();
                    System.out.print("Enter owner email: ");
                    email = new java.util.Scanner(System.in).nextLine();
                    System.out.print("Enter owner stake: ");
                    stake = new java.util.Scanner(System.in).nextFloat();
                    System.out.print("Enter owner investment: ");
                    investment = new java.util.Scanner(System.in).nextDouble();
                    Owner owner = new Owner(name, email, stake, investment);
                    owner.addOwner(owner);
                    break;

                case 0:
                    System.out.println("\nExiting Stakeholder Management...");
                    break;

                default:
                    System.out.println("\nInvalid choice. Please try again.");
            }
        } while (input != 0);
    }
}
