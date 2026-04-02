## 2024-05-14 - Transient Secrets Cleanup
**Vulnerability:** PDF passwords entered by users are kept in memory indefinitely by `PdfPasswordStore` in a stateful cache.
**Learning:** `PdfPasswordStore` caches passwords so `PdfiumRenderer` can use them when initializing. However, the store is never cleared after `PdfiumRenderer` successfully opens the document, leaving passwords in memory and vulnerable to memory dumps or heap inspection.
**Prevention:** Transient secrets (such as PDF passwords) must be explicitly cleared immediately after their use case is complete (like calling `forgetPassword` in `PdfPasswordStore` after successfully accessing the resource) to minimize exposure time to memory dumps or heap inspection.
