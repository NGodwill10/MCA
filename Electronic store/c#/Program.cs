using ElectronicStoreSystem.Models;
using ElectronicStoreSystem.Services;

namespace ElectronicStoreSystem;

public class Program
{
    public static void Main(string[] args)
    {
        int choice;
        do
        {
            Console.WriteLine("\n=====================================");
            Console.WriteLine(" ELECTRONIC STORE MANAGEMENT SYSTEM ");
            Console.WriteLine("=====================================");
            Console.WriteLine("1. Customer Management");
            Console.WriteLine("2. Staff Management");
            Console.WriteLine("3. Product Management");
            Console.WriteLine("4. Stakeholder Management");
            Console.WriteLine("0. Exit");
            Console.Write("Enter Choice: ");

            if (!int.TryParse(Console.ReadLine(), out choice))
            {
                choice = -1;
            }

            switch (choice)
            {
                case 1: CustomerMenu(); break;
                case 2: StaffMenu(); break;
                case 3: ProductMenu(); break;
                case 4: StakeholderMenu(); break;
                case 0: Console.WriteLine("Exiting system. Goodbye!"); break;
                default: Console.WriteLine("Invalid choice! Try again."); break;
            }
        } while (choice != 0);
    }

    private static void RemoveEntry(string filePath)
    {
        Console.Write("Enter ID to remove: ");
        if (!int.TryParse(Console.ReadLine(), out int targetId))
        {
            Console.WriteLine("Invalid ID format.");
            return;
        }

        var lines = FileManager.ReadAllLines(filePath);
        var updatedLines = new List<string>();
        bool removed = false;

        foreach (var line in lines)
        {
            var parts = line.Split(',');
            if (parts.Length > 0 && int.TryParse(parts[0], out int id) && id == targetId)
            {
                removed = true;
            }
            else
            {
                updatedLines.Add(line);
            }
        }

        if (removed)
        {
            FileManager.WriteAllLines(filePath, updatedLines);
            Console.WriteLine("Record removed successfully.");
        }
        else
        {
            Console.WriteLine("Record ID not found.");
        }
    }

    private static void ListEntries(string filePath)
    {
        var lines = FileManager.ReadAllLines(filePath);
        Console.WriteLine("\n--- Records ---");
        if (lines.Count == 0)
        {
            Console.WriteLine("No records found.");
        }
        else
        {
            foreach (var line in lines)
            {
                Console.WriteLine(line);
            }
        }
    }

    private static void CustomerMenu()
    {
        Console.WriteLine("\n--- Customer Management ---");
        Console.WriteLine("1. Add Customer\n2. Remove Customer\n3. List Customers\n4. Generate Bill\n5. Request Repair");
        Console.Write("Select: ");
        _ = int.TryParse(Console.ReadLine(), out int subChoice);

        switch (subChoice)
        {
            case 1:
                int id = AutoIdGenerator.GetNextId(AppConfig.CustomersFile);
                Console.Write("Enter Name: "); string name = Console.ReadLine() ?? "";
                Console.Write("Enter Address: "); string addr = Console.ReadLine() ?? "";
                Console.Write("Enter Contact: "); string contact = Console.ReadLine() ?? "";
                FileManager.AppendLine(AppConfig.CustomersFile, new Customer(id, name, addr, contact).ToCsv());
                Console.WriteLine($"Customer added with ID: {id}");
                break;
            case 2: RemoveEntry(AppConfig.CustomersFile); break;
            case 3: ListEntries(AppConfig.CustomersFile); break;
            case 4:
                var pIds = new List<int>();
                while (true)
                {
                    Console.Write("Enter Product ID to add to bill (0 to complete): ");
                    if (int.TryParse(Console.ReadLine(), out int pId) && pId == 0) break;
                    pIds.Add(pId);
                }
                StoreServices.ProcessBilling(pIds);
                break;
            case 5: StoreServices.RequestRepair(); break;
            default: Console.WriteLine("Invalid option."); break;
        }
    }

    private static void StaffMenu()
    {
        Console.WriteLine("\n--- Staff Management ---");
        Console.WriteLine("1. Add Staff\n2. Remove Staff\n3. List Staff");
        Console.Write("Select: ");
        _ = int.TryParse(Console.ReadLine(), out int subChoice);

        switch (subChoice)
        {
            case 1:
                int id = AutoIdGenerator.GetNextId(AppConfig.StaffFile);
                Console.Write("Enter Name: "); string name = Console.ReadLine() ?? "";
                Console.Write("Enter Address: "); string addr = Console.ReadLine() ?? "";
                Console.Write("Enter Salary: "); _ = double.TryParse(Console.ReadLine(), out double salary);
                FileManager.AppendLine(AppConfig.StaffFile, new Staff(id, name, addr, salary).ToCsv());
                Console.WriteLine($"Staff added with ID: {id}");
                break;
            case 2: RemoveEntry(AppConfig.StaffFile); break;
            case 3: ListEntries(AppConfig.StaffFile); break;
            default: Console.WriteLine("Invalid option."); break;
        }
    }

    private static void ProductMenu()
    {
        Console.WriteLine("\n--- Product Management ---");
        Console.WriteLine("1. Add Product\n2. Remove Product\n3. List Products");
        Console.Write("Select: ");
        _ = int.TryParse(Console.ReadLine(), out int subChoice);

        switch (subChoice)
        {
            case 1:
                int id = AutoIdGenerator.GetNextId(AppConfig.ProductsFile);
                Console.Write("Enter Name: "); string name = Console.ReadLine() ?? "";
                Console.Write("Enter Category: "); string category = Console.ReadLine() ?? "";
                Console.Write("Enter Price: "); _ = double.TryParse(Console.ReadLine(), out double price);
                FileManager.AppendLine(AppConfig.ProductsFile, new Product(id, name, category, price).ToCsv());
                Console.WriteLine($"Product added with ID: {id}");
                break;
            case 2: RemoveEntry(AppConfig.ProductsFile); break;
            case 3: ListEntries(AppConfig.ProductsFile); break;
            default: Console.WriteLine("Invalid option."); break;
        }
    }

    private static void StakeholderMenu()
    {
        Console.WriteLine("\n--- Stakeholder Management ---");
        Console.WriteLine("1. Add Stakeholder\n2. Remove Stakeholder\n3. List Stakeholders");
        Console.Write("Select: ");
        _ = int.TryParse(Console.ReadLine(), out int subChoice);

        switch (subChoice)
        {
            case 1:
                int id = AutoIdGenerator.GetNextId(AppConfig.StakeholdersFile);
                Console.Write("Enter Name: "); string name = Console.ReadLine() ?? "";
                Console.Write("Enter Email: "); string email = Console.ReadLine() ?? "";
                Console.Write("Enter Type (Owner/Investor): "); string type = Console.ReadLine() ?? "";
                Console.Write("Enter Stake %: "); _ = double.TryParse(Console.ReadLine(), out double stake);
                Console.Write("Enter Investment: "); _ = double.TryParse(Console.ReadLine(), out double inv);
                FileManager.AppendLine(AppConfig.StakeholdersFile, new Stakeholder(id, name, email, type, stake, inv).ToCsv());
                Console.WriteLine($"Stakeholder added with ID: {id}");
                break;
            case 2: RemoveEntry(AppConfig.StakeholdersFile); break;
            case 3: ListEntries(AppConfig.StakeholdersFile); break;
            default: Console.WriteLine("Invalid option."); break;
        }
    }
}