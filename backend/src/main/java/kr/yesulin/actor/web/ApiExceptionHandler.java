package kr.yesulin.actor.web;

import kr.yesulin.actor.document.CompletedFileName;
import kr.yesulin.actor.document.DocumentStore;
import kr.yesulin.actor.document.HwpDocumentException;
import kr.yesulin.actor.document.PdfConverter;
import kr.yesulin.actor.document.UploadValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Turns failures into the codes the app shows messages for, and logs them: expected rejections as one
 * line, processing failures with their cause, anything unexpected with a stack trace.
 */
@RestControllerAdvice
public final class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DocumentStore.DocumentNotFoundException.class)
    ResponseEntity<ApiError> notFound(DocumentStore.DocumentNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(DocumentStore.StoreFullException.class)
    ResponseEntity<ApiError> storeFull(DocumentStore.StoreFullException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "SERVER_BUSY", exception.getMessage());
    }

    @ExceptionHandler(PdfConverter.PdfUnavailableException.class)
    ResponseEntity<ApiError> pdfUnavailable(PdfConverter.PdfUnavailableException exception) {
        log.error("PDF renderer missing: {}", exception.getMessage());
        return response(HttpStatus.SERVICE_UNAVAILABLE, "PDF_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(UploadValidator.InvalidUploadException.class)
    ResponseEntity<ApiError> invalidUpload(UploadValidator.InvalidUploadException exception) {
        log.info("rejected upload: {}", exception.getMessage());
        return response(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD", exception.getMessage());
    }

    @ExceptionHandler(CompletedFileName.InvalidFileNameException.class)
    ResponseEntity<ApiError> invalidFileName(CompletedFileName.InvalidFileNameException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_FILE_NAME", exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ApiError> invalidRequest(Exception exception) {
        // A validation message can quote the rejected value (an applicant's answer), so only its fields are logged.
        String detail = exception instanceof MethodArgumentNotValidException invalid
                ? invalid.getFieldErrors().stream().map(error -> error.getField()).toList().toString()
                : exception.getMessage();
        log.warn("invalid request {}: {}", exception.getClass().getSimpleName(), detail);
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException exception) {
        log.info("rejected upload: over size limit");
        return response(HttpStatusCode.valueOf(413), "UPLOAD_TOO_LARGE", "업로드 용량 제한을 초과했습니다.");
    }

    @ExceptionHandler(HwpDocumentException.class)
    ResponseEntity<ApiError> invalidDocument(HwpDocumentException exception) {
        log.warn("document processing failed: {}", exception.getMessage(), exception.getCause());
        return response(HttpStatusCode.valueOf(422), "HWP_PROCESSING_FAILED", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        if (exception instanceof ErrorResponse framework) {
            // Spring's own 404/405/415 answers: keep their status, they are not server faults.
            return response(framework.getStatusCode(), "INVALID_REQUEST", "요청을 처리할 수 없습니다.");
        }
        log.error("unexpected failure", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_ERROR", "처리하지 못했습니다.");
    }

    private static ResponseEntity<ApiError> response(
            HttpStatusCode status, String code, String message) {
        return ResponseEntity.status(status).body(ApiError.of(code, message));
    }
}
