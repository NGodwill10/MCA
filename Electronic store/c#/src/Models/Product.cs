namespace ElectronicStoreSystem.Models;

public class Product
{
    public int Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string Category { get; set; } = string.Empty;
    public double Price { get; set; }

    public Product(int id, string name, string category, double price)
    {
        Id = id;
        Name = name;
        Category = category;
        Price = price;
    }

    public string ToCsv() => $"{Id},{Name},{Category},{Price}";
}