package service;

import java.util.List;

public class AutoId {
    public static int getNextId(String filePath) {
        try {
            List<String> lines = FileService.readAllLines(filePath);
            for (int i = lines.size() - 1; i >= 0; i--) {
                String[] fields = lines.get(i).split(",", -1);
                try {
                    return Integer.parseInt(fields[0].trim()) + 1;
                } catch (NumberFormatException e) {
                    if (fields.length > 1) {
                        try {
                            return Integer.parseInt(fields[1].trim()) + 1;
                        } catch (NumberFormatException ignored) {
                            // Skip records without a numeric ID.
                        }
                    }
                }
            }
            return 1;
        } catch (Exception e) {
            System.out.println("Error reading ID from file: " + e.getMessage());
            return 1;
        }
    }
}
