package io.store.filestorage.repository;

import io.store.filestorage.entity.FileChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FileChunkRepository extends JpaRepository<FileChunk, UUID> {
}
