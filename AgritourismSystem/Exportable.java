/**
 * Interface representing export management for agricultural entities.
 * 
 * @author Yeshwant
 * @version 1.0
 */
public interface Exportable {
    /**
     * Calculates export tax based on yield volume.
     * 
     * @param quantityInTons Yield in tons
     * @return Calculated tax amount
     */
    double calculateExportTax(double quantityInTons);
}