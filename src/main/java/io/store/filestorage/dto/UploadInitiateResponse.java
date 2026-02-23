package io.store.filestorage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadInitiateResponse {

    private UUID fileId;
    private String uploadId;
    private List<ChunkUrl> chunkUrls;
    private int expiresIn;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChunkUrl {
        private int partNumber;
        private String uploadUrl;
    }
}