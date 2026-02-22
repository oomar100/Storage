package io.store.filestorage.repository;

import io.store.filestorage.entity.FileMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {
    Page<FileMetadata> findByUserId(String userId, Pageable pageable);
    Optional<FileMetadata> findByIdAndUserId(UUID id, String userId);
}
