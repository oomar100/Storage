package io.store.filestorage;

import io.store.filestorage.entity.FileMetadata;
import io.store.filestorage.entity.FileStatus;
import io.store.filestorage.repository.FileMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@DataJpaTest
class FileMetadataRepositoryTest {

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    private FileMetadata userAFile;
    private FileMetadata userBFile;

    @BeforeEach
    void setUp() {
        fileMetadataRepository.deleteAll();

        userAFile = fileMetadataRepository.save(FileMetadata.builder()
                .userId("user-a")
                .filename("report.pdf")
                .fileType("application/pdf")
                .fileSize(1024L)
                .status(FileStatus.COMPLETED)
                .s3Key("uploads/abc/report.pdf")
                .s3UploadId("upload-1")
                .chunkCount(1)
                .uploadDate(Instant.now())
                .build());

        userBFile = fileMetadataRepository.save(FileMetadata.builder()
                .userId("user-b")
                .filename("photo.png")
                .fileType("image/png")
                .fileSize(2048L)
                .status(FileStatus.PENDING)
                .s3Key("uploads/def/photo.png")
                .s3UploadId("upload-2")
                .chunkCount(2)
                .uploadDate(Instant.now())
                .build());
    }

    @Test
    void findByUserId_returnsOnlyThatUsersFiles() {
        Page<FileMetadata> results = fileMetadataRepository.findByUserId("user-a", PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getFilename()).isEqualTo("report.pdf");
    }

    @Test
    void findByUserId_returnsEmptyForUnknownUser() {
        Page<FileMetadata> results = fileMetadataRepository.findByUserId("user-unknown", PageRequest.of(0, 10));

        assertThat(results.getContent()).isEmpty();
    }

    @Test
    void findByIdAndUserId_returnsFileWhenOwnerMatches() {
        Optional<FileMetadata> result = fileMetadataRepository.findByIdAndUserId(userAFile.getId(), "user-a");

        assertThat(result).isPresent();
        assertThat(result.get().getFilename()).isEqualTo("report.pdf");
    }

    @Test
    void findByIdAndUserId_returnsEmptyWhenOwnerDoesNotMatch() {
        Optional<FileMetadata> result = fileMetadataRepository.findByIdAndUserId(userAFile.getId(), "user-b");

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdAndUserId_returnsEmptyForNonexistentFile() {
        Optional<FileMetadata> result = fileMetadataRepository.findByIdAndUserId(
                java.util.UUID.randomUUID(), "user-a");

        assertThat(result).isEmpty();
    }
}