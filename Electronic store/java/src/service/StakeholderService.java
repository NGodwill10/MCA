package service;

import model.Investor;
import model.Owner;
import java.util.Scanner;

public class StakeholderService {

    public StakeholderService(Scanner scanner) {
        int input;
        do {

            System.out.println("\n--- Stakeholder Management ---");
            System.out.println("1. Add Investor");
            System.out.println("2. Add Owner");
            System.out.println("0. Back to Main Menu");

            input = scanner.nextInt();
            scanner.nextLine();

            switch (input) {
                case 1:
                    int id = AutoId.getNextId("data/stakeholders.txt");
                    System.out.print("Enter investor name: ");
                    String name = scanner.nextLine();
                    System.out.print("Enter investor email: ");
                    String email = scanner.nextLine();
                    System.out.print("Enter investor stake: ");
                    float stake = scanner.nextFloat();
                    System.out.print("Enter investor investment: ");
                    double investment = scanner.nextDouble();

                    Investor investor = new Investor(id, name, email, stake, investment);
                    investor.addInvestor(investor);
                    break;

                case 2:
                    System.out.print("Enter owner name: ");
                    name = scanner.nextLine();
                    System.out.print("Enter owner email: ");
                    email = scanner.nextLine();
                    System.out.print("Enter owner stake: ");
                    stake = scanner.nextFloat();
                    System.out.print("Enter owner investment: ");
                    investment = scanner.nextDouble();
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
