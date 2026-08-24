namespace ElectronicStoreSystem.Models;

public class Staff
{
    public int Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string Address { get; set; } = string.Empty;
    public double Salary { get; set; }

    public Staff(int id, string name, string address, double salary)
    {
        Id = id;
        Name = name;
        Address = address;
        Salary = salary;
    }

    public string ToCsv() => $"{Id},{Name},{Address},{Salary}";
}