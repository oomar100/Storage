package io.store.filestorage.dto;

import io.store.filestorage.entity.FileStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileResponse {

    private UUID fileId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private FileStatus status;
    private Instant uploadDate;
    private Instant completedAt;
}
