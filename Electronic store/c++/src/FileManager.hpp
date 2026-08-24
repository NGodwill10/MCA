#ifndef FILEMANAGER_HPP
#define FILEMANAGER_HPP

#include <iostream>
#include <fstream>
#include <vector>
#include <string>
#include <filesystem>

namespace fs = std::filesystem;

class FileManager {
public:
    static void ensureFileExists(const std::string& filePath) {
        fs::path p(filePath);
        if (p.has_parent_path() && !fs::exists(p.parent_path())) {
            fs::create_directories(p.parent_path());
        }
        if (!fs::exists(p)) {
            std::ofstream outfile(filePath);
            outfile.close();
        }
    }

    static std::vector<std::string> readAllLines(const std::string& filePath) {
        ensureFileExists(filePath);
        std::vector<std::string> lines;
        std::ifstream file(filePath);
        std::string line;
        while (std::getline(file, line)) {
            if (!line.empty()) {
                lines.push_back(line);
            }
        }
        file.close();
        return lines;
    }

    static void appendLine(const std::string& filePath, const std::string& line) {
        ensureFileExists(filePath);
        std::ofstream file(filePath, std::ios::app);
        if (file.is_open()) {
            file << line << "\n";
            file.close();
        }
    }

    static void writeAllLines(const std::string& filePath, const std::vector<std::string>& lines) {
        ensureFileExists(filePath);
        std::ofstream file(filePath, std::ios::trunc);
        if (file.is_open()) {
            for (const auto& line : lines) {
                file << line << "\n";
            }
            file.close();
        }
    }
};

#endif