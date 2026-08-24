namespace ElectronicStoreSystem.Services;

public static class AppConfig
{
    // Centralized directory configuration
    public static string DataDir { get; set; } = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "data");

    public static string CustomersFile => Path.Combine(DataDir, "customers.txt");
    public static string ProductsFile => Path.Combine(DataDir, "products.txt");
    public static string StaffFile => Path.Combine(DataDir, "staff.txt");
    public static string StakeholdersFile => Path.Combine(DataDir, "stakeholders.txt");
    public static string StockFile => Path.Combine(DataDir, "stock.txt");
}