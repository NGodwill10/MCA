package data;

import java.io.IOException;
import java.util.List;
import model.Member;

public interface MemberRepository {
    List<Member> findAll() throws IOException;

    void saveAll(List<Member> members) throws IOException;
}