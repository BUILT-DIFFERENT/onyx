# Blob Storage

- S3-compatible object storage (provider TBD).
- Raw blobs only: attachments, exports, backups, thumbnails.
- Convex issues presigned URLs for PUT/GET and tracks metadata.
- Public links are resolved in Convex, then served via presigned GET.
