namespace ElectronicStoreSystem.Services;

public static class AutoIdGenerator
{
    public static int GetNextId(string filePath)
    {
        var lines = FileManager.ReadAllLines(filePath);
        if (lines.Count == 0)
        {
            return 1;
        }

        string lastLine = lines.Last();
        string[] parts = lastLine.Split(',');

        if (parts.Length > 0 && int.TryParse(parts[0], out int lastId))
        {
            return lastId + 1;
        }

        return 1;
    }
}