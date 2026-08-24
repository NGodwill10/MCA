#ifndef AUTOIDGENERATOR_HPP
#define AUTOIDGENERATOR_HPP

#include "FileManager.hpp"
#include <sstream>

class AutoIdGenerator {
public:
    static int getNextId(const std::string& filePath) {
        std::vector<std::string> lines = FileManager::readAllLines(filePath);
        if (lines.empty()) {
            return 1;
        }
        std::string lastLine = lines.back();
        std::stringstream ss(lastLine);
        std::string idStr;
        if (std::getline(ss, idStr, ',')) {
            try {
                return std::stoi(idStr) + 1;
            } catch (...) {
                return 1;
            }
        }
        return 1;
    }
};

#endif