namespace ElectronicStoreSystem.Models;

public class Customer
{
    public int Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string Address { get; set; } = string.Empty;
    public string Contact { get; set; } = string.Empty;

    public Customer(int id, string name, string address, string contact)
    {
        Id = id;
        Name = name;
        Address = address;
        Contact = contact;
    }

    public string ToCsv() => $"{Id},{Name},{Address},{Contact}";
}