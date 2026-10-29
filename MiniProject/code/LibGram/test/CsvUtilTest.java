import data.CsvUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CsvUtilTest {
    private CsvUtilTest() { }

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("libgram-csv-");
        try {
            Path file = folder.resolve("values.txt");
            List<List<String>> expected = List.of(List.of("Smith, John", "\"Quoted\"", "line\none", "  padded  ", "देवनागरी é"));
            CsvUtil.writeAtomically(file, List.of("a", "b", "c", "d", "e"), expected);
            TestSupport.equals(expected, CsvUtil.read(file).subList(1, 2), "CSV quoting round-trip");
            CsvUtil.writeAtomically(file, List.of("a", "b", "c", "d", "e"), expected);
            TestSupport.check(Files.exists(file.resolveSibling("values.txt.bak")), "CSV backup exists after rewrite");
            System.out.println("CSV utility tests passed.");
        } finally {
            TestSupport.deleteTree(folder);
        }
    }
}
