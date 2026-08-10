//  Clas description:
/* 
    This class provides file-related services such as appending data to a file, removing a line from a file based on an ID, and reading all lines from a file.
    It uses the java.nio.file package for file operations and handles exceptions related to file I/O.

    methods:
    - appendData(String filePath, String content): Appends the specified content to the file at the given file path. If the file or its parent directories do not exist, they are created.
    - removeLineById(String filePath, int id): Removes a line from the file at the given file path that starts with the specified ID. If the file does not exist or no line with the given ID is found, an IOException is thrown.
    - readAllLines(String filePath): Reads all lines from the file at the given file path and returns them as a List of Strings. If the file does not exist, an empty list is returned.
    - writeAllLines(String filePath, List<String> lines): Writes the specified list of lines to the file at the given file path. If the file or its parent directories do not exist, they are created.
*/

package service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;

public class FileService {

    public static void appendData(String filePath, String content) throws IOException {
        Path path = Paths.get(filePath);

        // Create parent directories if they don't exist
        if (path.getParent() != null && !Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }

        // Create the file if it doesn't exist
        if (!Files.exists(path)) {
            Files.createFile(path);
        }

        // Append the content to the file
        Files.writeString(
                path,
                content + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    // Method to remove a line from a file based on the ID
    public static void removeLineById(String filePath, int id) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }

        List<String> lines = Files.readAllLines(path);
        String idString = String.valueOf(id);
        boolean removed = lines.removeIf(line -> line.startsWith(idString + ","));

        if (removed) {
            Files.write(path, lines);
        } else {
            throw new IOException("No line found with ID: " + id);
        }
    }

    public static List<String> readAllLines(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return Collections.emptyList();
        }
        return Files.readAllLines(path);
    }

    public static void writeAllLines(String filePath, List<String> lines) throws IOException {
        Path path = Paths.get(filePath);

        // Create parent directories if they don't exist
        if (path.getParent() != null && !Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }

        // Write all lines to the file
        Files.write(path, lines);
    }
}