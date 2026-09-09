/**
 * Represents farm lodging services.
 * Demonstrates <b>Hierarchical Inheritance</b> (both {@link Crop} and
 * {@link FarmStay} extend {@link Crop}).
 * 
 * @author Yeshwant
 * @version 1.0
 */
public class FarmStay extends Crop {
    private int numberOfCottages;

    /**
     * Constructs a FarmStay object.
     * 
     * @param farmName         Farm name
     * @param location         Farm location
     * @param cropName         Crop grown nearby
     * @param season           Season
     * @param numberOfCottages Cottage count
     */
    public FarmStay(String farmName, String location, String cropName, String season, int numberOfCottages) {
        super(farmName, location, cropName, season);
        this.numberOfCottages = numberOfCottages;
    }

    /** Displays cottage availability. */
    public void displayStayInfo() {
        displayCropDetails();
        System.out.println("Available Cottages for Tourist Stay: " + numberOfCottages);
    }
}