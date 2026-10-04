package kr.yesulin.actor.form;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.yesulin.actor.document.DocumentService;
import kr.yesulin.actor.document.DocumentStore;
import kr.yesulin.actor.document.GeneratedDocument;
import kr.yesulin.actor.document.HwpDocument;
import kr.yesulin.actor.document.HwpDocumentException;
import kr.yesulin.actor.document.JobContent;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds an applicant's file from one stored form version. Each applicant gets a job of their own,
 * copied from the shared source; the shared source and definition are only read.
 */
@Component
public final class FormBuilder {
    private final FormStore store;
    private final DocumentService documents;
    private final FormDefinitionParser parser;

    public FormBuilder(FormStore store, DocumentService documents, JsonMapper json) {
        this.store = store;
        this.documents = documents;
        this.parser = new FormDefinitionParser(json);
    }

    public record Built(UUID documentId, GeneratedDocument document, String editToken) {}

    /**
     * @param owner   the job tag: applicants' and test builds of a version never share jobs
     * @param counted whether this is an application (operator tests are not)
     */
    Built build(Vid vid, int version, String owner, FormViews.GenerateRequest request,
            Map<String, MultipartFile> photos, boolean counted) throws IOException, HwpDocumentException {
        return build(vid, version, owner, request, photos, counted, null);
    }

    Built build(Vid vid, int version, String owner, FormViews.GenerateRequest request,
            Map<String, MultipartFile> photos, boolean counted, String editToken) throws IOException, HwpDocumentException {
        FormDefinition definition = definition(vid, version);
        Path source = source(vid, version);
        FormAnswers answers = FormAnswers.of(definition, request.answers(), photos);
        JobContent content = FormComposer.compose(
                definition, answers, SourceCheck.texts(HwpDocument.open(source).cells()));
        if (content.writes().isEmpty()) {
            throw new InvalidAnswerException("입력한 내용이 없어요");
        }
        // The applicant's own choice wins; the operator's template is only the suggestion.
        String fileName = request.fileName() == null || request.fileName().isBlank()
                ? FormComposer.fileName(definition, answers)
                : request.fileName();
        // Operator tests have already passed admin authentication; public applicants need the capability.
        if (request.documentId() != null && (!counted || editToken != null)) {
            try {
                if (counted) documents.verifyEditToken(request.documentId(), editToken);
                return new Built(request.documentId(),
                        documents.buildJob(request.documentId(), owner, fileName, content, counted),
                        documents.editToken(request.documentId()));
            } catch (DocumentStore.DocumentNotFoundException expired) {
                // The job expired (30 minutes) or belongs elsewhere: the answers are all here, so start afresh.
            }
        }
        UUID job = documents.startJob(store.info(vid, version).originalName(), source, owner);
        return new Built(job, documents.buildJob(job, owner, fileName, content, counted), documents.editToken(job));
    }

    FormDefinition definition(Vid vid, int version) throws IOException {
        String text = store.definition(vid, version)
                .orElseThrow(() -> new FormExceptions.NotReady(List.of("양식 정의(JSON)를 아직 저장하지 않았습니다.")));
        try {
            return parser.parse(text);
        } catch (InvalidFormDefinitionException invalid) {
            throw new FormExceptions.NotReady(invalid.problems());
        }
    }

    Path source(Vid vid, int version) {
        return store.source(vid, version)
                .orElseThrow(() -> new FormExceptions.NotReady(List.of("원본 지원서 파일을 아직 올리지 않았습니다.")));
    }

    /** Fingerprint of what a test build proved: the source file and the definition text together. */
    String fingerprint(Vid vid, int version) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            var source = store.source(vid, version);
            if (source.isPresent()) {
                digest.update(Files.readAllBytes(source.get()));
            }
            digest.update((byte) 0);
            digest.update(store.definition(vid, version).orElse("").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", missing);
        }
    }
}
