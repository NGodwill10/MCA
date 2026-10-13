package src;

import java.io.FileWriter;
import java.io.IOException;

public class ReportHandler {
    public static void saveReport(String data) {
        try {
            FileWriter writer = new FileWriter("nirf_report.txt");
            writer.write(data);
            writer.close();
        } catch (IOException e) {
            System.out.println("IO Error encountered.");
        } finally {
            System.out.println("Persistence operation executed.");
        }
    }
}