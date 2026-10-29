package data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import model.Member;
import model.MemberStatus;
import model.MembershipType;

public final class CsvMemberRepository implements MemberRepository {
    private static final List<String> HEADER = List.of(
            "memberId", "name", "phone", "email", "address", "membershipType", "joinDate", "expiryDate",
            "status", "createdAt", "updatedAt");

    private final Path file;
    private List<Member> members;

    public CsvMemberRepository(Path file) throws IOException {
        this.file = file;
        members = loadMembers();
        if (!Files.exists(file) || Files.size(file) == 0) {
            writeMembers();
        }
    }

    @Override
    public List<Member> findAll() {
        return List.copyOf(members);
    }

    @Override
    public void saveAll(List<Member> members) throws IOException {
        List<Member> updatedMembers = List.copyOf(members);
        writeMembers(updatedMembers);
        this.members = updatedMembers;
    }

    private List<Member> loadMembers() throws IOException {
        List<List<String>> records = CsvUtil.read(file);
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> header = records.getFirst();
        List<Member> loaded = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < records.size(); rowIndex++) {
            List<String> row = records.get(rowIndex);
            try {
                loaded.add(new Member(value(row, header, "memberId"), value(row, header, "name"),
                        value(row, header, "phone"), value(row, header, "email"), value(row, header, "address"),
                        MembershipType.valueOf(value(row, header, "membershipType").toUpperCase(Locale.ROOT)),
                        LocalDate.parse(value(row, header, "joinDate")),
                        LocalDate.parse(value(row, header, "expiryDate")),
                        MemberStatus.valueOf(value(row, header, "status").toUpperCase(Locale.ROOT)),
                        LocalDateTime.parse(value(row, header, "createdAt")),
                        LocalDateTime.parse(value(row, header, "updatedAt"))));
            } catch (RuntimeException exception) {
                System.getLogger(CsvMemberRepository.class.getName()).log(System.Logger.Level.WARNING,
                        "Skipping malformed member row " + (rowIndex + 1) + " in " + file, exception);
            }
        }
        return List.copyOf(loaded);
    }

    private void writeMembers() throws IOException {
        writeMembers(members);
    }

    private void writeMembers(List<Member> records) throws IOException {
        List<List<String>> rows = records.stream().map(member -> List.of(
                member.memberId(), member.name(), member.phone(), member.email(), member.address(),
                member.membershipType().name(), member.joinDate().toString(), member.expiryDate().toString(),
                member.status().name(), member.createdAt().toString(), member.updatedAt().toString())).toList();
        CsvUtil.writeAtomically(file, HEADER, rows);
    }

    private static String value(List<String> row, List<String> header, String name) {
        int index = header.indexOf(name);
        return index < 0 || index >= row.size() ? "" : row.get(index);
    }
}