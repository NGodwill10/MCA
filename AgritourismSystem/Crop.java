/**
 * Represents specific crops cultivated on the farm.
 * Demonstrates <b>Single Inheritance</b> by extending {@link AgriEntity}.
 * 
 * @author Yeshwant
 * @version 1.0
 */
public class Crop extends AgriEntity {
    /** Name of the cultivated crop. */
    protected String cropName;

    /** Harvesting or growth season. */
    protected String season;

    /**
     * Constructs a Crop object.
     * 
     * @param farmName Farm name
     * @param location Farm location
     * @param cropName Crop name
     * @param season   Harvesting season
     */
    public Crop(String farmName, String location, String cropName, String season) {
        super(farmName, location);
        this.cropName = cropName;
        this.season = season;
    }

    /** Displays crop details. */
    public void displayCropDetails() {
        displayFarmDetails();
        System.out.println("Crop: " + cropName + " | Season: " + season);
    }
}