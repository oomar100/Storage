package io.store.filestorage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadCompleteRequest {

    @NotBlank
    private String uploadId;

    @NotEmpty
    private List<PartETag> parts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartETag {
        private int partNumber;
        private String eTag;
    }
}
