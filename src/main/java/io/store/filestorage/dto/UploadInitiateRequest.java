package io.store.filestorage.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadInitiateRequest {

    @NotBlank
    private String fileName;

    @NotBlank
    private String fileType;

    @Positive
    private Long fileSize;

    @Min(1)
    private int chunkCount;
}
