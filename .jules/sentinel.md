## 2024-05-24 - Clearing transient PDF passwords from memory
**Vulnerability:** Transient PDF passwords were kept indefinitely within `PdfPasswordStore` even after successfully opening the PDF, increasing the window of exposure.
**Learning:** Transient secrets must be cleared immediately after their use case is complete to minimize exposure time to memory dumps or heap inspection.
**Prevention:** Ensure the software architecture enforces immediate clearing of secrets (like calling `forgetPassword`) after the associated resources are successfully accessed.
