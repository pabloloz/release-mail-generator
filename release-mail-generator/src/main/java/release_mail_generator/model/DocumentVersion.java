package release_mail_generator.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_versions", indexes = {
    @Index(name = "idx_doc_type", columnList = "documentType"),
    @Index(name = "idx_doc_ref", columnList = "documentRef"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentVersion {

    @Id
    private String id;

    /** RFC | UAT | RELEASE | RDL | TELEGRAM | RDL_TELEGRAM */
    @Column(nullable = false, length = 20)
    private String documentType;

    /** Identificador del documento: RFC number, versión, nombre del RDL, etc. */
    @Column(length = 500)
    private String documentRef;

    /** Título legible */
    @Column(length = 500)
    private String title;

    /** Quién generó/modificó */
    @Column(length = 200)
    private String author;

    /** Contenido generado (HTML o texto plano) */
    @Lob
    @Column(columnDefinition = "CLOB")
    private String content;

    /** SHA-256 del contenido para comparación rápida */
    @Column(length = 64)
    private String contentHash;

    /** CREATED | MODIFIED | EXPORTED | RESTORED */
    @Column(nullable = false, length = 20)
    private String action;

    /** Formato: HTML | TEXT | PDF | MARKDOWN */
    @Column(length = 20)
    private String format;

    @Column(nullable = false)
    private int versionNumber;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Tamaño del contenido en bytes (para UI) */
    @Column
    private long contentSize;
}
