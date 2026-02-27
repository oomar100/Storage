## High level overview

A cloud-based file storage service that lets users upload, download, and manage files through a web interface. Files are chunked client-side and uploaded directly to S3 via pre-signed URLs, keeping the backend lightweight since it never handles raw file bytes. The backend manages file metadata, orchestrates multipart uploads, and generates temporary access URLs. Authentication is handled through Cognito, with API Gateway validating tokens before requests reach the service. Built with React and Mantine on the frontend, Spring Boot and PostgreSQL on the backend, deployed on AWS using ECS, RDS, S3, CloudFront, API Gateway, and Cognito.

<img width="1646" height="991" alt="Untitled-2026-02-22-0033(1)" src="https://github.com/user-attachments/assets/c261c641-9bd9-4fd9-aaa7-15bdd564f9e3" />


### Demo

[![Watch the demo](https://img.youtube.com/vi/DnfERluEboE/0.jpg)](https://youtu.be/DnfERluEboE)


## Entities

### FileMetadata

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | Primary key |
| `userId` | String | Owner (from Cognito JWT `sub`) |
| `fileName` | String | Original name from client |
| `fileType` | String | MIME type (e.g. `image/png`) |
| `fileSize` | Long | Size in bytes |
| `status` | Enum | `PENDING` / `COMPLETED` / `FAILED` |
| `s3Key` | String | Object key in S3 bucket |
| `s3UploadId` | String | Multipart upload ID from S3 |
| `chunkCount` | int | Total number of chunks |
| `uploadDate` | Instant | When upload was initiated |
| `completedAt` | Instant | When upload was confirmed |

### FileChunk

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | Primary key |
| `fileId` | UUID | FK → FileMetadata |
| `partNumber` | int | Chunk order index |
| `s3ETag` | String | ETag returned by S3 after part upload |
| `size` | Long | Chunk size in bytes |
| `status` | Enum | `PENDING` / `COMPLETED` |

### Folder
| Field       | Type    | Notes                                                   |
| ----------- | ------- | ------------------------------------------------------- |
| `id`        | UUID    | Primary key                                             |
| `userId`    | String  | Owner                                                   |
| `name`      | String  | Folder name (not full path)                             |
| `parentId`  | UUID    | Self-reference → parent `Folder.id` (nullable for root) |
| `createdAt` | Instant | Folder creation timestamp                               |

---

## API Endpoints

### File

| # | Method | Endpoint | Description |
|---|--------|----------|-------------|
| 1 | POST | `/api/files` | Initiate multipart upload |
| 2 | PUT | `/api/files/{fileId}` | Complete upload |
| 3 | DELETE | `/api/files/{fileId}/upload` | Abort upload |
| 4 | GET | `/api/files/{fileId}/content` | Get pre-signed download URL |
| 5 | GET | `/api/files` | List files (paginated) |
| 6 | GET | `/api/files/{fileId}` | Get file details |
| 7 | DELETE | `/api/files/{fileId}` | Delete file |


### Folder

| # | Method | Endpoint                  | Description                              |
| - | ------ | ------------------------- | ---------------------------------------- |
| 1 | POST   | `/api/folders`            | Create new folder                        |
| 2 | GET    | `/api/folders`            | Get root contents                        |
| 3 | GET    | `/api/folders/{folderId}` | Get folder contents (subfolders + files) |
| 4 | PUT    | `/api/folders/{folderId}` | Rename folder                            |
| 5 | DELETE | `/api/folders/{folderId}` | Delete folder (recursive)                |


---

## Flows

### File Upload

```
Step  Action                              Services
───── ───────────────────────────────── ──────────────────────
1     User selects files                  React
2     React calls POST /api/files         API Gateway → ECS
3     Spring Boot creates metadata        ECS → RDS
4     Spring Boot initiates multipart     ECS → S3
5     Pre-signed chunk URLs returned      ECS → API Gateway → React
6     React uploads chunks directly       React → S3
7     React calls PUT /api/files/{id}     API Gateway → ECS
8     Spring Boot completes multipart     ECS → S3
9     Metadata status → COMPLETED         ECS → RDS
```

### File Download

```
Step  Action                              Services
───── ───────────────────────────────── ──────────────────────
1     User clicks download                React
2     React calls GET /{id}/content       API Gateway → ECS
3     Spring Boot generates GET URL       ECS → S3
4     Pre-signed download URL returned    ECS → API Gateway → React
5     React opens URL directly            React → S3
```

### File Delete

```
Step  Action                              Services
───── ───────────────────────────────── ──────────────────────
1     User clicks delete                  React
2     React calls DELETE /api/files/{id}  API Gateway → ECS
3     Spring Boot deletes S3 object       ECS → S3
4     Spring Boot deletes metadata        ECS → RDS
```


### Create Folder
```
Step  Action                                  Services
───── ────────────────────────────────────── ──────────────────────
1     User clicks "New Folder"               React
2     React calls POST /api/folders          API Gateway → ECS
3     Spring Boot validates parentId         ECS → RDS
4     Folder entity saved                    ECS → RDS
5     FolderResponse returned                ECS → API Gateway → React
```

### List Root Contents

```
Step  Action                                  Services
───── ────────────────────────────────────── ──────────────────────
1     User opens file manager                React
2     React calls GET /api/folders           API Gateway → ECS
3     Backend loads folders (parentId=null)  ECS → RDS
4     Backend loads files (folderId=null)    ECS → RDS
5     FolderContentsResponse returned        ECS → API Gateway → React

```

### List Folder

```
Step  Action                                      Services
───── ────────────────────────────────────────── ──────────────────────
1     User clicks folder                         React
2     React calls GET /api/folders/{folderId}    API Gateway → ECS
3     Backend validates ownership                ECS → RDS
4     Load subfolders (parentId=folderId)        ECS → RDS
5     Load files (folderId=folderId)             ECS → RDS
6     FolderContentsResponse returned            ECS → API Gateway → React
```

### Rename Folder

```
Step  Action                                      Services
───── ────────────────────────────────────────── ──────────────────────
1     User renames folder                        React
2     React calls PUT /api/folders/{id}          API Gateway → ECS
3     Backend validates ownership                ECS → RDS
4     Update folder.name                         ECS → RDS
5     Updated FolderResponse returned            ECS → API Gateway → React
```

### Delete Folder
```
Step  Action                                        Services
───── ──────────────────────────────────────────── ──────────────────────
1     User clicks delete                           React
2     React calls DELETE /api/folders/{id}         API Gateway → ECS
3     Backend validates ownership                  ECS → RDS
4     Recursively load child folders               ECS → RDS
5     Collect all descendant files                 ECS → RDS
6     Delete S3 objects for all files              ECS → S3
7     Delete file metadata                         ECS → RDS
8     Delete folders (bottom-up)                   ECS → RDS
9     204 No Content returned                      ECS → API Gateway → React
```
### Authentication

```
Step  Action                              Services
───── ───────────────────────────────── ──────────────────────
1     User signs up / signs in            React → Cognito
2     Cognito returns JWT                 Cognito → React
3     React sends JWT on every request    React → API Gateway
4     API Gateway validates JWT           API Gateway → Cognito (JWKS)
5     Request forwarded to backend        API Gateway → ECS
6     Spring Boot extracts userId (sub)   ECS
```
