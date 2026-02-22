package io.store.filestorage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "file_chunk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata fileMetadata;

    @Column(nullable = false)
    private int partNumber;

    private String s3ETag;

    private Long size;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileStatus status;
}