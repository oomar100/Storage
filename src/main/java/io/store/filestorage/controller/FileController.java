package io.store.filestorage.controller;


import io.store.filestorage.dto.*;
import io.store.filestorage.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    // 1. Initiate upload
    @PostMapping
    public ResponseEntity<UploadInitiateResponse> initiateUpload(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UploadInitiateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fileService.initiateUpload(userId, request));
    }

    // 2. Complete upload
    @PutMapping("/{fileId}")
    public ResponseEntity<FileResponse> completeUpload(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID fileId,
            @Valid @RequestBody UploadCompleteRequest request) {
        return ResponseEntity.ok(fileService.completeUpload(userId, fileId, request));
    }

    // 3. Abort upload
    @DeleteMapping("/{fileId}/upload")
    public ResponseEntity<Void> abortUpload(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID fileId) {
        fileService.abortUpload(userId, fileId);
        return ResponseEntity.noContent().build();
    }

    // 4. Get download URL
    @GetMapping("/{fileId}/content")
    public ResponseEntity<DownloadResponse> getDownloadUrl(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID fileId) {
        return ResponseEntity.ok(fileService.getDownloadUrl(userId, fileId));
    }

    // 5. List files
    @GetMapping
    public ResponseEntity<Page<FileResponse>> listFiles(
            @RequestHeader("X-User-Id") String userId,
            Pageable pageable) {
        return ResponseEntity.ok(fileService.listFiles(userId, pageable));
    }

    // 6. Get file details
    @GetMapping("/{fileId}")
    public ResponseEntity<FileResponse> getFile(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID fileId) {
        return ResponseEntity.ok(fileService.getFile(userId, fileId));
    }

    // 7. Delete file
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID fileId) {
        fileService.deleteFile(userId, fileId);
        return ResponseEntity.noContent().build();
    }
}