/**
 * Interface representing booking capabilities for tourism entities.
 * Demonstrates Multiple/Hybrid Inheritance when implemented alongside
 * interfaces.
 * 
 * @author Yeshwant
 * @version 1.0
 */
public interface Bookable {
    /**
     * Books a tour or stay package.
     * 
     * @param slots Number of visitors/slots to book
     */
    void bookPackage(int slots);
}
