namespace ElectronicStoreSystem.Services;

public static class FileManager
{
    public static void EnsureFileExists(string filePath)
    {
        string? directory = Path.GetDirectoryName(filePath);
        if (!string.IsNullOrEmpty(directory) && !Directory.Exists(directory))
        {
            Directory.CreateDirectory(directory);
        }
        if (!File.Exists(filePath))
        {
            using (File.Create(filePath)) { }
        }
    }

    public static List<string> ReadAllLines(string filePath)
    {
        EnsureFileExists(filePath);
        return File.ReadAllLines(filePath)
                   .Where(line => !string.IsNullOrWhiteSpace(line))
                   .ToList();
    }

    public static void AppendLine(string filePath, string line)
    {
        EnsureFileExists(filePath);
        File.AppendAllLines(filePath, new[] { line });
    }

    public static void WriteAllLines(string filePath, List<string> lines)
    {
        EnsureFileExists(filePath);
        File.WriteAllLines(filePath, lines);
    }
}