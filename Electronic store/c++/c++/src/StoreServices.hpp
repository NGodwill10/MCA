#ifndef STORESERVICES_HPP
#define STORESERVICES_HPP

#include "FileManager.hpp"
#include "Models.hpp"
#include <iostream>
#include <iomanip>
#include <sstream>

class StoreServices {
private:
    inline static const std::string STOCK_FILE = "data/stock.txt";
    inline static const std::string PRODUCT_FILE = "data/products.txt";
    static const int BATCH_SIZE = 10;

public:
    static void supplierDeliverItem(int productId, int quantity) {
        std::cout << "[Supplier] Delivering " << quantity << " units of Product ID " << productId << " to Warehouse...\n";
        
        std::vector<std::string> lines = FileManager::readAllLines(STOCK_FILE);
        std::vector<StockItem> stocks;
        bool found = false;

        for (const auto& line : lines) {
            std::stringstream ss(line);
            std::string pIdStr, sQtyStr, wQtyStr;
            std::getline(ss, pIdStr, ',');
            std::getline(ss, sQtyStr, ',');
            std::getline(ss, wQtyStr, ',');

            int pId = std::stoi(pIdStr);
            int sQty = std::stoi(sQtyStr);
            int wQty = std::stoi(wQtyStr);

            if (pId == productId) {
                wQty += quantity;
                found = true;
            }
            stocks.push_back({pId, sQty, wQty});
        }

        if (!found) {
            stocks.push_back({productId, 0, quantity});
        }

        std::vector<std::string> outLines;
        for (const auto& s : stocks) {
            outLines.push_back(s.toString());
        }
        FileManager::writeAllLines(STOCK_FILE, outLines);
    }

    static void supplierOrderItem(int productId) {
        std::cout << "[Warehouse] Out of stock. Requesting order from Supplier (Batch size: " << BATCH_SIZE << ")...\n";
        supplierDeliverItem(productId, BATCH_SIZE);
    }

    static bool checkAndFulfillStock(int productId) {
        std::vector<std::string> lines = FileManager::readAllLines(STOCK_FILE);
        std::vector<StockItem> stocks;
        bool found = false;
        bool fulfilled = false;

        for (const auto& line : lines) {
            std::stringstream ss(line);
            std::string pIdStr, sQtyStr, wQtyStr;
            std::getline(ss, pIdStr, ',');
            std::getline(ss, sQtyStr, ',');
            std::getline(ss, wQtyStr, ',');

            int pId = std::stoi(pIdStr);
            int sQty = std::stoi(sQtyStr);
            int wQty = std::stoi(wQtyStr);

            if (pId == productId) {
                found = true;
                if (sQty > 0) {
                    sQty--;
                    fulfilled = true;
                } else {
                    std::cout << "[Store] Product out of stock in Store. Checking Warehouse...\n";
                    if (wQty <= 0) {
                        supplierOrderItem(productId);
                        return checkAndFulfillStock(productId);
                    }
                    wQty--;
                    sQty += (BATCH_SIZE - 1);
                    std::cout << "[Store] Transferred batch stock from Warehouse to Store.\n";
                    fulfilled = true;
                }
            }
            stocks.push_back({pId, sQty, wQty});
        }

        if (!found) {
            std::cout << "[System] Initializing stock record for Product ID " << productId << "...\n";
            supplierOrderItem(productId);
            return checkAndFulfillStock(productId);
        }

        std::vector<std::string> outLines;
        for (const auto& s : stocks) {
            outLines.push_back(s.toString());
        }
        FileManager::writeAllLines(STOCK_FILE, outLines);
        return fulfilled;
    }

    static void requestRepair() {
        std::cout << "\n-----------------------------\n";
        std::cout << "repair handled\n";
        std::cout << "-----------------------------\n";
    }

    static void processBilling(const std::vector<int>& productIds) {
        std::vector<std::string> prodLines = FileManager::readAllLines(PRODUCT_FILE);
        double total = 0.0;

        std::cout << "\n==================================\n";
        std::cout << "          RECEIPT / BILL          \n";
        std::cout << "==================================\n";
        std::cout << std::left << std::setw(8) << "ID" << std::setw(15) << "Name" << std::setw(10) << "Price" << "\n";
        std::cout << "----------------------------------\n";

        for (int pId : productIds) {
            if (checkAndFulfillStock(pId)) {
                for (const auto& line : prodLines) {
                    std::stringstream ss(line);
                    std::string idStr, name, cat, priceStr;
                    std::getline(ss, idStr, ',');
                    std::getline(ss, name, ',');
                    std::getline(ss, cat, ',');
                    std::getline(ss, priceStr, ',');

                    if (std::stoi(idStr) == pId) {
                        double price = std::stod(priceStr);
                        total += price;
                        std::cout << std::left << std::setw(8) << pId 
                                  << std::setw(15) << name 
                                  << " Rs. " << std::fixed << std::setprecision(2) << price << "\n";
                        break;
                    }
                }
            }
        }
        std::cout << "----------------------------------\n";
        std::cout << "TOTAL AMOUNT: Rs. " << std::fixed << std::setprecision(2) << total << "\n";
        std::cout << "==================================\n\n";
    }
};

#endif