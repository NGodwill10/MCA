/**
 * Combines agriculture and tourism features.
 * Demonstrates:
 * <ul>
 * <li><b>Multilevel Inheritance:</b> AgriEntity &rarr; Crop &rarr;
 * AgriTourismPackage</li>
 * <li><b>Multiple / Hybrid Inheritance:</b> Extends {@link Crop} and implements
 * {@link Bookable}, {@link Exportable}</li>
 * </ul>
 * 
 * @author Yeshwant
 * @version 1.0
 */
public class AgriTourismPackage extends Crop implements Bookable, Exportable {
    private double pricePerPerson;
    private int availableSlots;

    /**
     * Constructs an AgriTourismPackage.
     * 
     * @param farmName       Farm name
     * @param location       Farm location
     * @param cropName       Primary crop
     * @param season         Season
     * @param pricePerPerson Tour price per person
     * @param availableSlots Available visitor slots
     */
    public AgriTourismPackage(String farmName, String location, String cropName, String season, double pricePerPerson,
            int availableSlots) {
        super(farmName, location, cropName, season);
        this.pricePerPerson = pricePerPerson;
        this.availableSlots = availableSlots;
    }

    @Override
    public void bookPackage(int slots) {
        if (slots <= availableSlots) {
            availableSlots -= slots;
            System.out.println(" Successfully booked " + slots + " slot(s) for " + farmName + " tour.");
            System.out.println("Total Amount: ₹ " + (slots * pricePerPerson));
        } else {
            System.out.println(" Booking failed! Only " + availableSlots + " slots available.");
        }
    }

    @Override
    public double calculateExportTax(double quantityInTons) {
        double tax = quantityInTons * 1500.0;
        System.out.println("Export Tax for " + quantityInTons + " tons of " + cropName + ": ₹ " + tax);
        return tax;
    }
}