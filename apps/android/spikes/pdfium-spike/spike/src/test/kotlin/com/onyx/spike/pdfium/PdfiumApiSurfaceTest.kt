package com.onyx.spike.pdfium

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

/**
 * P0.1 Spike: Verify PdfiumAndroid API surface for Phase 2 parity requirements.
 *
 * This test inspects the PdfiumAndroid Java API via reflection to verify:
 * - Document lifecycle (newDocument/closeDocument)
 * - Page lifecycle (openPage/closePage)
 * - Rendering (renderPageBitmap)
 * - Text extraction (getPageText)
 * - Character geometry (getCharBox, getCharOrigin)
 * - Table of contents (getTableOfContents)
 *
 * NOTE: This is a source-level inspection test. It does NOT require Android runtime.
 * It verifies API method signatures exist and can be called (reflection).
 */
@DisplayName("P0.1 PdfiumAndroid API Surface Verification")
class PdfiumApiSurfaceTest {
    // The PdfiumCore class from the PdfiumAndroid library
    private val pdfiumCoreClassName = "com.shockwave.pdfium.PdfiumCore"
    private val pdfDocumentClassName = "com.shockwave.pdfium.PdfDocument"
    private val testClassLoader = javaClass.classLoader

    /**
     * Load classes without running static initializers.
     * PdfiumCore's static init touches Android logging/native load paths and crashes in plain JVM tests.
     */
    private fun loadClass(className: String): Class<*> = Class.forName(className, false, testClassLoader)

    @Nested
    @DisplayName("Document Lifecycle APIs")
    inner class DocumentLifecycleTests {
        @Test
        @DisplayName("Verify newDocument methods exist")
        fun verifyNewDocumentMethods() {
            val pdfiumCoreClass = loadClass(pdfiumCoreClassName)
            val methods = pdfiumCoreClass.declaredMethods.filter { it.name == "newDocument" }

            println("\n=== newDocument Methods ===")
            methods.forEach { method ->
                println("  ${method.toGenericString()}")
            }

            assert(methods.isNotEmpty()) { "No newDocument methods found in PdfiumCore" }

            // Check for common signatures: newDocument(ParcelFileDescriptor) and newDocument(byte[])
            val hasFdMethod =
                methods.any { m ->
                    m.parameterTypes.any { it.name.contains("ParcelFileDescriptor") }
                }
            val hasBytesMethod =
                methods.any { m ->
                    m.parameterTypes.any { it == ByteArray::class.java }
                }

            println("  Has ParcelFileDescriptor variant: $hasFdMethod")
            println("  Has byte[] variant: $hasBytesMethod")

            // Document what was found for parity report
            if (!hasFdMethod && !hasBytesMethod) {
                println("  WARNING: Expected ParcelFileDescriptor or byte[] parameter variants")
            }
        }

        @Test
        @DisplayName("Verify closeDocument method exists")
        fun verifyCloseDocumentMethod() {
            val pdfiumCoreClass = loadClass(pdfiumCoreClassName)
            val methods = pdfiumCoreClass.declaredMethods.filter { it.name == "closeDocument" }

            println("\n=== closeDocument Method ===")
            methods.forEach { method ->
                println("  ${method.toGenericString()}")
            }

            assert(methods.isNotEmpty()) { "No closeDocument method found in PdfiumCore" }
        }
    }

    @Nested
    @DisplayName("Page Lifecycle APIs")
    inner class PageLifecycleTests {
        @Test
        @DisplayName("Verify openPage method exists")
        fun verifyOpenPageMethod() {
            val pdfiumCoreClass = loadClass(pdfiumCoreClassName)
            val methods = pdfiumCoreClass.declaredMethods.filter { it.name == "openPage" }

            println("\n=== openPage Method ===")
            methods.forEach { method ->
                println("  ${method.toGenericString()}")
            }

            assert(methods.isNotEmpty()) { "No openPage method found in PdfiumCore" }
        }

        @Test
        @DisplayName("Verify closePage method exists (may not be present in all wrappers)")
        fun verifyClosePageMethod() {
            val pdfiumCoreClass = loadClass(pdfiumCoreClassName)
            val methods = pdfiumCoreClass.declaredMethods.filter { it.name == "closePage" }

            println("\n=== closePage Method ===")
            if (methods.isEmpty()) {
                println("  NOT FOUND - Pages may auto-close or use alternative lifecycle")
                println("  ACTION: Verify page lifecycle in PdfDocument class or documentation")
            } else {
                methods.forEach { method ->
                    println("  ${method.toGenericString()}")
                }
            }
        }

        @Test
        @DisplayName("Verify getPageSize method exists")
        fun verifyGetPageSizeMethod() {
            val pdfiumCoreClass = loadClass(pdfiumCoreClassName)
            val methods =
                pdfiumCoreClass.declaredMethods.filter {
                    it.name == "getPageSize" || it.name == "getPageWidth" || it.name == "getPageHeight"
                }

            println("\n=== Page Size Methods ===")
            methods.forEach { method ->
                println("  ${method.toGenericString()}")
            }

            assert(methods.isNotEmpty()) { "No page size methods found in PdfiumCore" }
        }
    }

    @Nested
    @DisplayName("Rendering APIs")
    inner class RenderingTests {
        @Test
        @DisplayName("Verify renderPageBitmap method exists and check coordinate params")
        fun verifyRenderPageBitmapMethod() {
            val pdfiumCoreClass = loadClass(pdfiumCoreClassName)
            val methods = pdfiumCoreClass.declaredMethods.filter { it.name == "renderPageBitmap" }

            println("\n=== renderPageBitmap Method ===")
            methods.forEach { method ->
                println("  ${method.toGenericString()}")
                println("  Parameters:")
                method.parameterTypes.forEachIndexed { index, param ->
                    println("    [$index] ${param.name}")
                }
            }

            assert(methods.isNotEmpty()) { "No renderPageBitmap method found in PdfiumCore" }

            // CRITICAL: Document the coordinate parameter semantics
            println("\n  COORDINATE CONTRACT (requires runtime verification):")
            println("  - startX/startY params: Check if negative values accepted for tile offsets")
            println("  - Units: Device pixels vs page points")
            println("  - ACTION: Run PdfiumRenderCoordinateTest on device to verify")
        }
    }

    @Nested
    @DisplayName("Text Extraction APIs")
    inner class TextExtractionTests {
        @Test
        @DisplayName("Verify getPageText method exists")
        fun verifyGetPageTextMethod() {
            val pdfiumCoreClass = loadClass(pdfiumCoreClassName)
            val methods =
                pdfiumCoreClass.declaredMethods.filter {
                    it.name.contains("Text", ignoreCase = true) &&
                        (it.name.contains("getPage", ignoreCase = true) || it.name.contains("extract", ignoreCase = true))
                }

            println("\n=== Text Extraction Methods ===")
            methods.forEach { method ->
                println("  ${method.toGenericString()}")
            }

            if (methods.isEmpty()) {
                println("  NOT FOUND - Text extraction may require native bridge")
                println("  ACTION: Inspect PdfiumAndroid native code for FPDFText_* functions")
            }
        }

        @Test
        @DisplayName("Verify character geometry methods (CRITICAL for text selection)")
        fun verifyCharGeometryMethods() {
            val pdfiumCoreClass = loadClass(pdfiumCoreClassName)
            val methods =
                pdfiumCoreClass.declaredMethods.filter {
                    it.name.contains("Char", ignoreCase = true) ||
                        it.name.contains("Box", ignoreCase = true) ||
                        it.name.contains("Origin", ignoreCase = true) ||
                        it.name.contains("Position", ignoreCase = true) ||
                        it.name.contains("Quad", ignoreCase = true)
                }

            println("\n=== Character Geometry Methods ===")
            if (methods.isEmpty()) {
                println("  NOT FOUND in Java API")
                println("  VERDICT: JNI_BRIDGE_REQUIRED for text selection geometry")
                println("  Native APIs needed: FPDFText_GetCharBox, FPDFText_GetCharOrigin, FPDFText_CountChars")
            } else {
                methods.forEach { method ->
                    println("  ${method.toGenericString()}")
                }
                println("  VERDICT: JAVA_API_SUFFICIENT (verify return types)")
            }
        }
    }

    @Nested
    @DisplayName("Navigation APIs")
    inner class NavigationTests {
        @Test
        @DisplayName("Verify getTableOfContents / bookmark methods exist")
        fun verifyTableOfContentsMethod() {
            val pdfiumCoreClass = loadClass(pdfiumCoreClassName)
            val methods =
                pdfiumCoreClass.declaredMethods.filter {
                    it.name.contains("Contents", ignoreCase = true) ||
                        it.name.contains("Bookmark", ignoreCase = true) ||
                        it.name.contains("Outline", ignoreCase = true) ||
                        it.name.contains("TOC", ignoreCase = true)
                }

            println("\n=== Table of Contents Methods ===")
            methods.forEach { method ->
                println("  ${method.toGenericString()}")
            }

            if (methods.isEmpty()) {
                println("  NOT FOUND - TOC may require alternative approach")
            }
        }
    }

    @Nested
    @DisplayName("Rotation APIs")
    inner class RotationTests {
        @Test
        @DisplayName("Verify getPageRotation method exists")
        fun verifyGetPageRotationMethod() {
            val pdfiumCoreClass = loadClass(pdfiumCoreClassName)
            val methods =
                pdfiumCoreClass.declaredMethods.filter {
                    it.name.contains("Rotation", ignoreCase = true)
                }

            println("\n=== Rotation Methods ===")
            methods.forEach { method ->
                println("  ${method.toGenericString()}")
            }

            if (methods.isEmpty()) {
                println("  NOT FOUND - Rotation may be handled internally or via different API")
            }
        }
    }

    @Nested
    @DisplayName("Password Protection APIs")
    inner class PasswordTests {
        @Test
        @DisplayName("Verify password-related methods exist")
        fun verifyPasswordMethods() {
            val pdfiumCoreClass = loadClass(pdfiumCoreClassName)
            val methods =
                pdfiumCoreClass.declaredMethods.filter {
                    it.name.contains("Password", ignoreCase = true) ||
                        it.name.contains("password", ignoreCase = true)
                }

            println("\n=== Password Methods ===")
            methods.forEach { method ->
                println("  ${method.toGenericString()}")
            }

            if (methods.isEmpty()) {
                println("  NOT FOUND in method names - check newDocument password parameter")
            }

            // Also check newDocument signature for password param
            val newDocMethods = pdfiumCoreClass.declaredMethods.filter { it.name == "newDocument" }
            println("\n  Checking newDocument signatures for password parameter:")
            newDocMethods.forEach { method ->
                val hasPassword =
                    method.parameterTypes.any {
                        it == String::class.java
                    }
                println("    ${method.toGenericString()} - has String param: $hasPassword")
            }
        }
    }

    @Nested
    @DisplayName("Complete API Surface Dump")
    inner class ApiSurfaceDump {
        @Test
        @DisplayName("Dump all PdfiumCore methods for reference")
        fun dumpAllMethods() {
            val pdfiumCoreClass = loadClass(pdfiumCoreClassName)
            val methods = pdfiumCoreClass.declaredMethods.sortedBy { it.name }

            println("\n=== COMPLETE PdfiumCore API SURFACE ===")
            println("Total methods: ${methods.size}")
            println("-".repeat(80))
            methods.forEach { method ->
                println(
                    "  ${method.returnType.simpleName.padEnd(
                        15,
                    )} ${method.name}(${method.parameterTypes.joinToString(", ") { it.simpleName }})",
                )
            }
        }

        @Test
        @DisplayName("Dump PdfDocument class structure")
        fun dumpPdfDocumentClass() {
            val pdfDocClass = loadClass(pdfDocumentClassName)

            println("\n=== PdfDocument CLASS STRUCTURE ===")
            println("Fields:")
            pdfDocClass.declaredFields.forEach { field ->
                println("  ${field.type.simpleName} ${field.name}")
            }

            println("\nMethods:")
            pdfDocClass.declaredMethods.sortedBy { it.name }.forEach { method ->
                println(
                    "  ${method.returnType.simpleName.padEnd(
                        15,
                    )} ${method.name}(${method.parameterTypes.joinToString(", ") { it.simpleName }})",
                )
            }
        }
    }
}
