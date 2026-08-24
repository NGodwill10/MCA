#include <iostream>
#include <vector>
#include <string>
#include <sstream>
#include "src/Models.hpp"
#include "src/FileManager.hpp"
#include "src/AutoIdGenerator.hpp"
#include "src/StoreServices.hpp"

const std::string CUST_FILE = "data/customers.txt";
const std::string STAFF_FILE = "data/staff.txt";
const std::string PROD_FILE = "data/products.txt";
const std::string STAKE_FILE = "data/stakeholders.txt";

void removeEntry(const std::string& file) {
    std::cout << "Enter ID to remove: ";
    int targetId;
    std::cin >> targetId;
    std::cin.ignore();

    std::vector<std::string> lines = FileManager::readAllLines(file);
    std::vector<std::string> updatedLines;
    bool removed = false;

    for (const auto& line : lines) {
        std::stringstream ss(line);
        std::string idStr;
        std::getline(ss, idStr, ',');
        if (std::stoi(idStr) == targetId) {
            removed = true;
        } else {
            updatedLines.push_back(line);
        }
    }

    if (removed) {
        FileManager::writeAllLines(file, updatedLines);
        std::cout << "Record deleted successfully.\n";
    } else {
        std::cout << "Record ID not found.\n";
    }
}

void listEntries(const std::string& file) {
    std::vector<std::string> lines = FileManager::readAllLines(file);
    std::cout << "\n--- Records ---\n";
    if (lines.empty()) {
        std::cout << "No records found.\n";
    } else {
        for (const auto& line : lines) {
            std::cout << line << "\n";
        }
    }
}

void customerMenu() {
    int choice;
    std::cout << "\n--- Customer Management ---\n";
    std::cout << "1. Add Customer\n2. Remove Customer\n3. List Customers\n4. Generate Bill\n5. Request Repair\nSelect: ";
    std::cin >> choice;
    std::cin.ignore();

    switch (choice) {
        case 1: {
            int id = AutoIdGenerator::getNextId(CUST_FILE);
            std::string name, addr, contact;
            std::cout << "Enter Name: "; std::getline(std::cin, name);
            std::cout << "Enter Address: "; std::getline(std::cin, addr);
            std::cout << "Enter Contact: "; std::getline(std::cin, contact);
            FileManager::appendLine(CUST_FILE, Customer{id, name, addr, contact}.toString());
            std::cout << "Customer added with ID: " << id << "\n";
            break;
        }
        case 2: removeEntry(CUST_FILE); break;
        case 3: listEntries(CUST_FILE); break;
        case 4: {
            std::vector<int> prodIds;
            int pid;
            while (true) {
                std::cout << "Enter Product ID to add to bill (0 to complete): ";
                std::cin >> pid;
                if (pid == 0) break;
                prodIds.push_back(pid);
            }
            std::cin.ignore();
            StoreServices::processBilling(prodIds);
            break;
        }
        case 5: StoreServices::requestRepair(); break;
        default: std::cout << "Invalid choice.\n";
    }
}

void staffMenu() {
    int choice;
    std::cout << "\n--- Staff Management ---\n";
    std::cout << "1. Add Staff\n2. Remove Staff\n3. List Staff\nSelect: ";
    std::cin >> choice;
    std::cin.ignore();

    switch (choice) {
        case 1: {
            int id = AutoIdGenerator::getNextId(STAFF_FILE);
            std::string name, addr;
            double salary;
            std::cout << "Enter Name: "; std::getline(std::cin, name);
            std::cout << "Enter Address: "; std::getline(std::cin, addr);
            std::cout << "Enter Salary: "; std::cin >> salary;
            std::cin.ignore();
            FileManager::appendLine(STAFF_FILE, Staff{id, name, addr, salary}.toString());
            std::cout << "Staff added with ID: " << id << "\n";
            break;
        }
        case 2: removeEntry(STAFF_FILE); break;
        case 3: listEntries(STAFF_FILE); break;
        default: std::cout << "Invalid choice.\n";
    }
}

void productMenu() {
    int choice;
    std::cout << "\n--- Product Management ---\n";
    std::cout << "1. Add Product\n2. Remove Product\n3. List Products\nSelect: ";
    std::cin >> choice;
    std::cin.ignore();

    switch (choice) {
        case 1: {
            int id = AutoIdGenerator::getNextId(PROD_FILE);
            std::string name, cat;
            double price;
            std::cout << "Enter Product Name: "; std::getline(std::cin, name);
            std::cout << "Enter Category: "; std::getline(std::cin, cat);
            std::cout << "Enter Price: "; std::cin >> price;
            std::cin.ignore();
            FileManager::appendLine(PROD_FILE, Product{id, name, cat, price}.toString());
            std::cout << "Product added with ID: " << id << "\n";
            break;
        }
        case 2: removeEntry(PROD_FILE); break;
        case 3: listEntries(PROD_FILE); break;
        default: std::cout << "Invalid choice.\n";
    }
}

void stakeholderMenu() {
    int choice;
    std::cout << "\n--- Stakeholder Management ---\n";
    std::cout << "1. Add Stakeholder\n2. Remove Stakeholder\n3. List Stakeholders\nSelect: ";
    std::cin >> choice;
    std::cin.ignore();

    switch (choice) {
        case 1: {
            int id = AutoIdGenerator::getNextId(STAKE_FILE);
            std::string name, email, type;
            double stake, inv;
            std::cout << "Enter Name: "; std::getline(std::cin, name);
            std::cout << "Enter Email: "; std::getline(std::cin, email);
            std::cout << "Enter Type (Owner/Investor): "; std::getline(std::cin, type);
            std::cout << "Enter Stake %: "; std::cin >> stake;
            std::cout << "Enter Investment Amount: "; std::cin >> inv;
            std::cin.ignore();
            FileManager::appendLine(STAKE_FILE, Stakeholder{id, name, email, type, stake, inv}.toString());
            std::cout << "Stakeholder added with ID: " << id << "\n";
            break;
        }
        case 2: removeEntry(STAKE_FILE); break;
        case 3: listEntries(STAKE_FILE); break;
        default: std::cout << "Invalid choice.\n";
    }
}

int main() {
    int choice;
    do {
        std::cout << "\n=====================================\n";
        std::cout << " ELECTRONIC STORE MANAGEMENT SYSTEM \n";
        std::cout << "=====================================\n";
        std::cout << "1. Customer Management\n";
        std::cout << "2. Staff Management\n";
        std::cout << "3. Product Management\n";
        std::cout << "4. Stakeholder Management\n";
        std::cout << "0. Exit\n";
        std::cout << "Enter Choice: ";
        std::cin >> choice;
        std::cin.ignore();

        switch (choice) {
            case 1: customerMenu(); break;
            case 2: staffMenu(); break;
            case 3: productMenu(); break;
            case 4: stakeholderMenu(); break;
            case 0: std::cout << "Exiting system. Goodbye!\n"; break;
            default: std::cout << "Invalid option! Try again.\n";
        }
    } while (choice != 0);

    return 0;
}