package co.com.lab.jdbc;

import co.com.lab.model.document.Document;
import co.com.lab.model.document.gateways.DocumentRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DocumentRepositoryAdapter implements DocumentRepository {

    private static final RowMapper<Document> ROW = (rs, i) -> new Document(
            rs.getLong("id"),
            rs.getString("tenant_id"),
            rs.getString("organization_id"),
            rs.getString("team_id"),
            rs.getString("owner_id"),
            rs.getString("title"),
            rs.getString("body"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcClient jdbc;

    public DocumentRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Document> findById(long id) {
        return jdbc.sql("select * from documents where id = :id").param("id", id).query(ROW).optional();
    }

    @Override
    public List<Document> findByOrganization(String tenantId, String organizationId) {
        return jdbc.sql("select * from documents where tenant_id = :t and organization_id = :o order by id")
                .param("t", tenantId)
                .param("o", organizationId)
                .query(ROW)
                .list();
    }

    @Override
    public Document create(NewDocument d) {
        Long id = jdbc.sql("""
                        insert into documents (tenant_id, organization_id, team_id, owner_id, title, body)
                        values (:t, :o, :team, :owner, :title, :body)
                        returning id
                        """)
                .param("t", d.tenantId())
                .param("o", d.organizationId())
                .param("team", d.teamId())
                .param("owner", d.ownerId())
                .param("title", d.title())
                .param("body", d.body())
                .query(Long.class)
                .single();
        return findById(id).orElseThrow();
    }

    @Override
    public void update(long id, String title, String body) {
        jdbc.sql("update documents set title = :title, body = :body where id = :id")
                .param("title", title)
                .param("body", body)
                .param("id", id)
                .update();
    }

    @Override
    public void delete(long id) {
        jdbc.sql("delete from documents where id = :id").param("id", id).update();
    }
}
