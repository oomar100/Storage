package io.store.filestorage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadResponse {

    private UUID fileId;
    private String downloadUrl;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private int expiresIn;
}
