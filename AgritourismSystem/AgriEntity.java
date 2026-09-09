/**
 * Base class representing a general agricultural entity.
 * Root of Single, Multilevel, and Hierarchical Inheritance chains.
 * 
 * @author Yeshwant
 * @version 1.0
 */
public class AgriEntity {
    /** Name of the farm location. */
    protected String farmName;

    /** Geographic location of the farm. */
    protected String location;

    /**
     * Constructs an AgriEntity object.
     * 
     * @param farmName Name of the farm
     * @param location Location of the farm
     */
    public AgriEntity(String farmName, String location) {
        this.farmName = farmName;
        this.location = location;
    }

    /** Displays basic farm details. */
    public void displayFarmDetails() {
        System.out.println("Farm Name: " + farmName + " | Location: " + location);
    }
}
