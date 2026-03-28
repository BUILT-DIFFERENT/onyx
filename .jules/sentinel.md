## 2024-03-28 - Explicitly Clear Transient Secrets from Memory
**Vulnerability:** Transient secrets (like PDF passwords) read into memory to open files were not cleared after successful use, extending their exposure time to memory dumps.
**Learning:** External unmanaged resource loaders (like PdfiumRenderer) might not clear the password state internally after processing it, necessitating explicit action in the orchestrating code to forget the secret.
**Prevention:** Always ensure the software architecture enforces immediate clearing of secrets (like calling `forgetPassword` in `PdfPasswordStore`) immediately after the associated resources are successfully accessed.
