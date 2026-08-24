#ifndef MODELS_HPP
#define MODELS_HPP

#include <string>
#include <sstream>

struct Stakeholder {
    int id;
    std::string name;
    std::string email;
    std::string type; // Owner or Investor
    double stake;
    double investment;

    std::string toString() const {
        return std::to_string(id) + "," + name + "," + email + "," + type + "," + 
               std::to_string(stake) + "," + std::to_string(investment);
    }
};

struct Customer {
    int id;
    std::string name;
    std::string address;
    std::string contact;

    std::string toString() const {
        return std::to_string(id) + "," + name + "," + address + "," + contact;
    }
};

struct Staff {
    int id;
    std::string name;
    std::string address;
    double salary;

    std::string toString() const {
        return std::to_string(id) + "," + name + "," + address + "," + std::to_string(salary);
    }
};

struct Product {
    int id;
    std::string name;
    std::string category;
    double price;

    std::string toString() const {
        return std::to_string(id) + "," + name + "," + category + "," + std::to_string(price);
    }
};

struct StockItem {
    int productId;
    int storeQty;
    int warehouseQty;

    std::string toString() const {
        return std::to_string(productId) + "," + std::to_string(storeQty) + "," + std::to_string(warehouseQty);
    }
};

#endif