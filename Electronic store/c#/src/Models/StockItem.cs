namespace ElectronicStoreSystem.Models;

public class StockItem
{
    public int ProductId { get; set; }
    public int StoreQty { get; set; }
    public int WarehouseQty { get; set; }

    public StockItem(int productId, int storeQty, int warehouseQty)
    {
        ProductId = productId;
        StoreQty = storeQty;
        WarehouseQty = warehouseQty;
    }

    public string ToCsv() => $"{ProductId},{StoreQty},{WarehouseQty}";
}