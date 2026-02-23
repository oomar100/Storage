package io.store.filestorage.service;

import io.store.filestorage.dto.*;
import io.store.filestorage.entity.FileChunk;
import io.store.filestorage.entity.FileMetadata;
import io.store.filestorage.entity.FileStatus;
import io.store.filestorage.repository.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final FileMetadataRepository fileMetadataRepository;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.presigned-url-expiry}")
    private int presignedUrlExpiry;

    @Transactional
    public UploadInitiateResponse initiateUpload(String userId, UploadInitiateRequest request) {
        String s3Key = buildS3Key(request.getFileName());

        // Start S3 multipart upload
        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(request.getFileType())
                .build();

        CreateMultipartUploadResponse s3Response = s3Client.createMultipartUpload(createRequest);
        String uploadId = s3Response.uploadId();

        // Create metadata record
        FileMetadata metadata = FileMetadata.builder()
                .userId(userId)
                .filename(request.getFileName())
                .fileType(request.getFileType())
                .fileSize(request.getFileSize())
                .status(FileStatus.PENDING)
                .s3Key(s3Key)
                .s3UploadId(uploadId)
                .chunkCount(request.getChunkCount())
                .uploadDate(Instant.now())
                .build();

        // Create chunk records
        List<FileChunk> chunks = new ArrayList<>();
        for (int i = 1; i <= request.getChunkCount(); i++) {
            FileChunk chunk = FileChunk.builder()
                    .fileMetadata(metadata)
                    .partNumber(i)
                    .status(FileStatus.PENDING)
                    .build();
            chunks.add(chunk);
        }
        metadata.setChunks(chunks);

        FileMetadata saved = fileMetadataRepository.save(metadata);

        // Generate pre-signed URLs for each chunk
        List<UploadInitiateResponse.ChunkUrl> chunkUrls = new ArrayList<>();
        for (int i = 1; i <= request.getChunkCount(); i++) {
            String url = generateUploadPartUrl(s3Key, uploadId, i);
            chunkUrls.add(UploadInitiateResponse.ChunkUrl.builder()
                    .partNumber(i)
                    .uploadUrl(url)
                    .build());
        }

        return UploadInitiateResponse.builder()
                .fileId(saved.getId())
                .uploadId(uploadId)
                .chunkUrls(chunkUrls)
                .expiresIn(presignedUrlExpiry)
                .build();
    }

    @Transactional
    public FileResponse completeUpload(String userId, UUID fileId, UploadCompleteRequest request) {
        FileMetadata metadata = findFileOrThrow(userId, fileId);

        // Complete the S3 multipart upload
        List<CompletedPart> completedParts = request.getParts().stream()
                .map(part -> CompletedPart.builder()
                        .partNumber(part.getPartNumber())
                        .eTag(part.getETag())
                        .build())
                .toList();

        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(metadata.getS3Key())
                .uploadId(request.getUploadId())
                .multipartUpload(CompletedMultipartUpload.builder()
                        .parts(completedParts)
                        .build())
                .build();

        s3Client.completeMultipartUpload(completeRequest);

        // Update chunk statuses
        for (UploadCompleteRequest.PartETag part : request.getParts()) {
            metadata.getChunks().stream()
                    .filter(c -> c.getPartNumber() == part.getPartNumber())
                    .findFirst()
                    .ifPresent(chunk -> {
                        chunk.setS3ETag(part.getETag());
                        chunk.setStatus(FileStatus.COMPLETED);
                    });
        }

        metadata.setStatus(FileStatus.COMPLETED);
        metadata.setCompletedAt(Instant.now());

        FileMetadata saved = fileMetadataRepository.save(metadata);
        return toFileResponse(saved);
    }

    @Transactional
    public void abortUpload(String userId, UUID fileId) {
        FileMetadata metadata = findFileOrThrow(userId, fileId);

        // Abort S3 multipart upload
        AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(metadata.getS3Key())
                .uploadId(metadata.getS3UploadId())
                .build();

        s3Client.abortMultipartUpload(abortRequest);

        metadata.setStatus(FileStatus.FAILED);
        fileMetadataRepository.save(metadata);
    }

    public DownloadResponse getDownloadUrl(String userId, UUID fileId) {
        FileMetadata metadata = findFileOrThrow(userId, fileId);

        if (metadata.getStatus() != FileStatus.COMPLETED) {
            throw new IllegalStateException("File upload is not completed");
        }

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(presignedUrlExpiry))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(metadata.getS3Key())
                        .build())
                .build();

        String downloadUrl = s3Presigner.presignGetObject(presignRequest).url().toString();

        return DownloadResponse.builder()
                .fileId(metadata.getId())
                .downloadUrl(downloadUrl)
                .fileName(metadata.getFilename())
                .fileType(metadata.getFileType())
                .fileSize(metadata.getFileSize())
                .expiresIn(presignedUrlExpiry)
                .build();
    }

    public Page<FileResponse> listFiles(String userId, Pageable pageable) {
        return fileMetadataRepository.findByUserId(userId, pageable)
                .map(this::toFileResponse);
    }

    public FileResponse getFile(String userId, UUID fileId) {
        return toFileResponse(findFileOrThrow(userId, fileId));
    }

    @Transactional
    public void deleteFile(String userId, UUID fileId) {
        FileMetadata metadata = findFileOrThrow(userId, fileId);

        // Delete from S3
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(metadata.getS3Key())
                .build();

        s3Client.deleteObject(deleteRequest);

        fileMetadataRepository.delete(metadata);
    }

    // --- Private helpers ---

    private String buildS3Key(String fileName) {
        return "uploads/" + UUID.randomUUID() + "/" + fileName;
    }

    private String generateUploadPartUrl(String s3Key, String uploadId, int partNumber) {
        UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(presignedUrlExpiry))
                .uploadPartRequest(UploadPartRequest.builder()
                        .bucket(bucket)
                        .key(s3Key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .build())
                .build();

        return s3Presigner.presignUploadPart(presignRequest).url().toString();
    }

    private FileMetadata findFileOrThrow(String userId, UUID fileId) {
        return fileMetadataRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new RuntimeException("File not found: " + fileId));
    }

    private FileResponse toFileResponse(FileMetadata metadata) {
        return FileResponse.builder()
                .fileId(metadata.getId())
                .fileName(metadata.getFilename())
                .fileType(metadata.getFileType())
                .fileSize(metadata.getFileSize())
                .status(metadata.getStatus())
                .uploadDate(metadata.getUploadDate())
                .completedAt(metadata.getCompletedAt())
                .build();
    }
}