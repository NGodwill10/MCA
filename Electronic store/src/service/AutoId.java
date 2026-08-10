//  Class descriotion;
/*
   This class is used to generate unique IDs for
   customers and suppliers in the store management
   system. It get's the last used ID from the file
   passed in the arguments file and increments it to
   generate a new unique ID.
   It returns id as 1 if the file doesn't exist or the file is empty.
 */

package service;

public class AutoId {
    public static int getNextId(String filePath) {
        try {
            // Read all lines from the file
            var lines = FileService.readAllLines(filePath);

            // If the file is empty, return 1 as the next ID
            if (lines.isEmpty()) {
                return 1;
            }

            // Get the last line and parse it as an integer
            String lastLine = lines.get(lines.size() - 1);
            int lastId = Integer.parseInt(lastLine.trim());

            // Return the next ID by incrementing the last ID
            return lastId + 1;
        } catch (Exception e) {
            // If any exception occurs (e.g., file not found, parsing error), return 1 as
            // the next ID
            System.out.println("Error reading ID from file: " + e.getMessage());
            return 1;
        }
    }
}
