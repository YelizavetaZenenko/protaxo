package com.example.protaxo.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** Підтвердний документ до витрати (скан чека, накладна). Читається лише при завантаженні файлу. */
@Entity
@Table(name = "finance_attachments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "data")
public class FinanceAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_id", nullable = false)
    private Long operationId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(nullable = false)
    private byte[] data;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
