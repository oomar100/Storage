## High level overview

A cloud-based file storage service that lets users upload, download, and manage files through a web interface. Files are chunked client-side and uploaded directly to S3 via pre-signed URLs, keeping the backend lightweight since it never handles raw file bytes. The backend manages file metadata, orchestrates multipart uploads, and generates temporary access URLs. Authentication is handled through Cognito, with API Gateway validating tokens before requests reach the service. Built with React and Mantine on the frontend, Spring Boot and PostgreSQL on the backend, deployed on AWS using ECS, RDS, S3, CloudFront, API Gateway, and Cognito.

<img width="1646" height="991" alt="Untitled-2026-02-22-0033(1)" src="https://github.com/user-attachments/assets/c261c641-9bd9-4fd9-aaa7-15bdd564f9e3" />

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

---

## API Endpoints

| # | Method | Endpoint | Description |
|---|--------|----------|-------------|
| 1 | POST | `/api/files` | Initiate multipart upload |
| 2 | PUT | `/api/files/{fileId}` | Complete upload |
| 3 | DELETE | `/api/files/{fileId}/upload` | Abort upload |
| 4 | GET | `/api/files/{fileId}/content` | Get pre-signed download URL |
| 5 | GET | `/api/files` | List files (paginated) |
| 6 | GET | `/api/files/{fileId}` | Get file details |
| 7 | DELETE | `/api/files/{fileId}` | Delete file |

---

## Flows

### Upload

```
Step  Action                              Services
───── ───────────────────────────────── ──────────────────────
1     User selects files                  React (CloudFront/S3)
2     React calls POST /api/files         API Gateway → ECS
3     Spring Boot creates metadata        ECS → RDS
4     Spring Boot initiates multipart     ECS → S3
5     Pre-signed chunk URLs returned      ECS → API Gateway → React
6     React uploads chunks directly       React → S3
7     React calls PUT /api/files/{id}     API Gateway → ECS
8     Spring Boot completes multipart     ECS → S3
9     Metadata status → COMPLETED         ECS → RDS
```

### Download

```
Step  Action                              Services
───── ───────────────────────────────── ──────────────────────
1     User clicks download                React (CloudFront/S3)
2     React calls GET /{id}/content       API Gateway → ECS
3     Spring Boot generates GET URL       ECS → S3
4     Pre-signed download URL returned    ECS → API Gateway → React
5     React opens URL directly            React → S3
```

### Delete

```
Step  Action                              Services
───── ───────────────────────────────── ──────────────────────
1     User clicks delete                  React (CloudFront/S3)
2     React calls DELETE /api/files/{id}  API Gateway → ECS
3     Spring Boot deletes S3 object       ECS → S3
4     Spring Boot deletes metadata        ECS → RDS
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
6     Spring Boot extracts userId (sub)   ECS (Spring Security)
```
