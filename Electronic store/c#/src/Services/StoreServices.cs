using ElectronicStoreSystem.Models;

namespace ElectronicStoreSystem.Services;

public static class StoreServices
{
    private const int BatchSize = 10;

    public static void SupplierDeliverItem(int productId, int quantity)
    {
        Console.WriteLine($"[Supplier] Delivering {quantity} units of Product ID {productId} to Warehouse...");

        var lines = FileManager.ReadAllLines(AppConfig.StockFile);
        var stocks = new List<StockItem>();
        bool found = false;

        foreach (var line in lines)
        {
            var parts = line.Split(',');
            int pId = int.Parse(parts[0]);
            int sQty = int.Parse(parts[1]);
            int wQty = int.Parse(parts[2]);

            if (pId == productId)
            {
                wQty += quantity;
                found = true;
            }
            stocks.Add(new StockItem(pId, sQty, wQty));
        }

        if (!found)
        {
            stocks.Add(new StockItem(productId, 0, quantity));
        }

        FileManager.WriteAllLines(AppConfig.StockFile, stocks.Select(s => s.ToCsv()).ToList());
    }

    public static void SupplierOrderItem(int productId)
    {
        Console.WriteLine($"[Warehouse] Out of stock. Ordering batch of {BatchSize} from Supplier...");
        SupplierDeliverItem(productId, BatchSize);
    }

    public static bool CheckAndFulfillStock(int productId)
    {
        var lines = FileManager.ReadAllLines(AppConfig.StockFile);
        var stocks = new List<StockItem>();
        bool found = false;
        bool fulfilled = false;

        foreach (var line in lines)
        {
            var parts = line.Split(',');
            int pId = int.Parse(parts[0]);
            int sQty = int.Parse(parts[1]);
            int wQty = int.Parse(parts[2]);

            if (pId == productId)
            {
                found = true;
                if (sQty > 0)
                {
                    sQty--;
                    fulfilled = true;
                }
                else
                {
                    Console.WriteLine("[Store] Product out of stock in Store. Checking Warehouse...");
                    if (wQty <= 0)
                    {
                        SupplierOrderItem(productId);
                        return CheckAndFulfillStock(productId);
                    }
                    wQty--;
                    sQty += (BatchSize - 1);
                    Console.WriteLine("[Store] Transferred batch stock from Warehouse to Store.");
                    fulfilled = true;
                }
            }
            stocks.Add(new StockItem(pId, sQty, wQty));
        }

        if (!found)
        {
            Console.WriteLine($"[System] Initializing stock record for Product ID {productId}...");
            SupplierOrderItem(productId);
            return CheckAndFulfillStock(productId);
        }

        FileManager.WriteAllLines(AppConfig.StockFile, stocks.Select(s => s.ToCsv()).ToList());
        return fulfilled;
    }

    public static void RequestRepair()
    {
        Console.WriteLine("\n-----------------------------");
        Console.WriteLine("repair handled");
        Console.WriteLine("-----------------------------\n");
    }

    public static void ProcessBilling(List<int> productIds)
    {
        var prodLines = FileManager.ReadAllLines(AppConfig.ProductsFile);
        double total = 0.0;

        Console.WriteLine("\n==================================");
        Console.WriteLine("          RECEIPT / BILL          ");
        Console.WriteLine("==================================");
        Console.WriteLine($"{"ID",-8} {"Name",-15} {"Price",-10}");
        Console.WriteLine("----------------------------------");

        foreach (int pId in productIds)
        {
            if (CheckAndFulfillStock(pId))
            {
                foreach (var line in prodLines)
                {
                    var parts = line.Split(',');
                    if (int.Parse(parts[0]) == pId)
                    {
                        string name = parts[1];
                        double price = double.Parse(parts[3]);
                        total += price;
                        Console.WriteLine($"{pId,-8} {name,-15} ${price,-9:F2}");
                        break;
                    }
                }
            }
        }

        Console.WriteLine("----------------------------------");
        Console.WriteLine($"TOTAL AMOUNT: ${total:F2}");
        Console.WriteLine("==================================\n");
    }
}