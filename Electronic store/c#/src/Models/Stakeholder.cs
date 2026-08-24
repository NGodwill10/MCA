namespace ElectronicStoreSystem.Models;

public class Stakeholder
{
    public int Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string Type { get; set; } = string.Empty; // Owner or Investor
    public double Stake { get; set; }
    public double Investment { get; set; }

    public Stakeholder(int id, string name, string email, string type, double stake, double investment)
    {
        Id = id;
        Name = name;
        Email = email;
        Type = type;
        Stake = stake;
        Investment = investment;
    }

    public string ToCsv() => $"{Id},{Name},{Email},{Type},{Stake},{Investment}";
}