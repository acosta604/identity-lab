package co.com.lab.api;

import co.com.lab.api.security.JwtPrincipalMapper;
import co.com.lab.model.document.Document;
import co.com.lab.model.principal.Principal;
import co.com.lab.usecase.document.CreateDocumentUseCase;
import co.com.lab.usecase.document.DeleteDocumentUseCase;
import co.com.lab.usecase.document.GetDocumentUseCase;
import co.com.lab.usecase.document.ListDocumentsUseCase;
import co.com.lab.usecase.document.UpdateDocumentUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DocumentController {

    private final ListDocumentsUseCase list;
    private final GetDocumentUseCase get;
    private final CreateDocumentUseCase create;
    private final UpdateDocumentUseCase update;
    private final DeleteDocumentUseCase delete;

    public DocumentController(ListDocumentsUseCase list, GetDocumentUseCase get, CreateDocumentUseCase create,
                              UpdateDocumentUseCase update, DeleteDocumentUseCase delete) {
        this.list = list;
        this.get = get;
        this.create = create;
        this.update = update;
        this.delete = delete;
    }

    @GetMapping("/documents")
    public List<Document> list(@AuthenticationPrincipal Jwt jwt) {
        return list.execute(principal(jwt));
    }

    @GetMapping("/documents/{id}")
    public Document get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return get.execute(principal(jwt), id);
    }

    public record CreateRequest(String title, String body, String teamId) {
    }

    @PostMapping("/documents")
    public ResponseEntity<Document> create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateRequest body) {
        Document created = create.execute(principal(jwt),
                new CreateDocumentUseCase.Command(body.title(), body.body(), body.teamId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    public record UpdateRequest(String title, String body) {
    }

    @PutMapping("/documents/{id}")
    public Document update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody UpdateRequest body) {
        return update.execute(principal(jwt), id, new UpdateDocumentUseCase.Command(body.title(), body.body()));
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        delete.execute(principal(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /** Muestra que trae el token y que tuplas contextuales se generan con el. Ideal para aprender. */
    @GetMapping("/whoami")
    public Map<String, Object> whoami(@AuthenticationPrincipal Jwt jwt) {
        Principal p = principal(jwt);
        return Map.of(
                "claims", jwt.getClaims(),
                "contextual_tuples", p.membershipRelationships().stream().map(Object::toString).toList());
    }

    private static Principal principal(Jwt jwt) {
        return JwtPrincipalMapper.toPrincipal(jwt);
    }
}
