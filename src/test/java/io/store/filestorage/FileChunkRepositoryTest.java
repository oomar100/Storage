package io.store.filestorage;

import io.store.filestorage.entity.FileChunk;
import io.store.filestorage.entity.FileMetadata;
import io.store.filestorage.entity.FileStatus;
import io.store.filestorage.repository.FileChunkRepository;
import io.store.filestorage.repository.FileMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@DataJpaTest
class FileChunkRepositoryTest {

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private FileChunkRepository fileChunkRepository;

    private FileMetadata savedFile;

    @BeforeEach
    void setUp() {
        fileChunkRepository.deleteAll();
        fileMetadataRepository.deleteAll();

        FileMetadata file = FileMetadata.builder()
                .userId("user-a")
                .filename("video.mp4")
                .fileType("video/mp4")
                .fileSize(10_000_000L)
                .status(FileStatus.PENDING)
                .s3Key("uploads/xyz/video.mp4")
                .s3UploadId("upload-3")
                .chunkCount(3)
                .uploadDate(Instant.now())
                .build();

        for (int i = 1; i <= 3; i++) {
            FileChunk chunk = FileChunk.builder()
                    .fileMetadata(file)
                    .partNumber(i)
                    .status(FileStatus.PENDING)
                    .build();
            file.getChunks().add(chunk);
        }

        savedFile = fileMetadataRepository.save(file);
    }

    @Test
    void chunks_areCascadeSavedWithMetadata() {
        List<FileChunk> chunks = fileChunkRepository.findAll();

        assertThat(chunks).hasSize(3);
    }

    @Test
    void chunks_areOrderedByPartNumber() {
        FileMetadata fetched = fileMetadataRepository.findById(savedFile.getId()).orElseThrow();

        List<Integer> partNumbers = fetched.getChunks().stream()
                .map(FileChunk::getPartNumber)
                .toList();

        assertThat(partNumbers).containsExactly(1, 2, 3);
    }

    @Test
    void chunks_areDeletedWhenMetadataIsDeleted() {
        fileMetadataRepository.delete(savedFile);

        assertThat(fileChunkRepository.findAll()).isEmpty();
    }

    @Test
    void chunks_belongToCorrectParent() {
        List<FileChunk> chunks = fileChunkRepository.findAll();

        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.getFileMetadata().getId()).isEqualTo(savedFile.getId()));
    }
}