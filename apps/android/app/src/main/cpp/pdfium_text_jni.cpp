#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>

#include <mutex>
#include <vector>

namespace {
constexpr const char* kLogTag = "PdfiumTextJni";
constexpr const char* kPdfiumLibName = "libpdfium.so";
constexpr const char* kPdfiumFallbackLibName = "libjniPdfium.so";
constexpr int kBoxValuesPerChar = 4;
constexpr int kLeftOffset = 0;
constexpr int kRightOffset = 1;
constexpr int kBottomOffset = 2;
constexpr int kTopOffset = 3;

using FPDF_PAGE = void*;
using FPDF_TEXTPAGE = void*;

using FPDFText_LoadPage_Fn = FPDF_TEXTPAGE (*)(FPDF_PAGE page);
using FPDFText_ClosePage_Fn = void (*)(FPDF_TEXTPAGE text_page);
using FPDFText_CountChars_Fn = int (*)(FPDF_TEXTPAGE text_page);
using FPDFText_GetUnicode_Fn = unsigned int (*)(FPDF_TEXTPAGE text_page, int index);
using FPDFText_GetCharBox_Fn = int (*)(FPDF_TEXTPAGE text_page, int index, double* left, double* right, double* bottom, double* top);
using FPDFPage_GetRotation_Fn = int (*)(FPDF_PAGE page);

struct PdfiumSymbols {
    void* handle = nullptr;
    FPDFText_LoadPage_Fn load_page = nullptr;
    FPDFText_ClosePage_Fn close_page = nullptr;
    FPDFText_CountChars_Fn count_chars = nullptr;
    FPDFText_GetUnicode_Fn get_unicode = nullptr;
    FPDFText_GetCharBox_Fn get_char_box = nullptr;
    FPDFPage_GetRotation_Fn get_page_rotation = nullptr;
    bool loaded = false;
};

PdfiumSymbols g_symbols;
std::once_flag g_load_once;

void LogError(const char* message) {
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s", message);
}

template <typename T>
bool ResolveSymbol(void* handle, const char* name, T* out) {
    *out = reinterpret_cast<T>(dlsym(handle, name));
    if (*out == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Missing symbol %s", name);
        return false;
    }
    return true;
}

bool LoadPdfiumSymbols() {
    std::call_once(g_load_once, []() {
        void* handle = dlopen(kPdfiumLibName, RTLD_NOW | RTLD_LOCAL);
        if (handle == nullptr) {
            handle = dlopen(kPdfiumFallbackLibName, RTLD_NOW | RTLD_LOCAL);
        }
        if (handle == nullptr) {
            LogError("Failed to load pdfium shared library");
            return;
        }

        bool ok = true;
        ok &= ResolveSymbol(handle, "FPDFText_LoadPage", &g_symbols.load_page);
        ok &= ResolveSymbol(handle, "FPDFText_ClosePage", &g_symbols.close_page);
        ok &= ResolveSymbol(handle, "FPDFText_CountChars", &g_symbols.count_chars);
        ok &= ResolveSymbol(handle, "FPDFText_GetUnicode", &g_symbols.get_unicode);
        ok &= ResolveSymbol(handle, "FPDFText_GetCharBox", &g_symbols.get_char_box);
        ok &= ResolveSymbol(handle, "FPDFPage_GetRotation", &g_symbols.get_page_rotation);

        if (!ok) {
            dlclose(handle);
            return;
        }

        g_symbols.handle = handle;
        g_symbols.loaded = true;
    });
    return g_symbols.loaded;
}
}  // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_com_onyx_android_pdf_PdfiumNativeTextBridge_nativeExtractPageText(
    JNIEnv* env,
    jclass /*clazz*/,
    jlong page_ptr) {
    if (page_ptr == 0) {
        return nullptr;
    }
    if (!LoadPdfiumSymbols()) {
        return nullptr;
    }

    auto page = reinterpret_cast<FPDF_PAGE>(page_ptr);
    auto text_page = g_symbols.load_page(page);
    if (text_page == nullptr) {
        LogError("FPDFText_LoadPage returned null");
        return nullptr;
    }

    std::vector<jint> code_points;
    std::vector<jfloat> boxes;

    const int char_count = g_symbols.count_chars(text_page);
    if (char_count > 0) {
        code_points.reserve(char_count);
        boxes.reserve(char_count * kBoxValuesPerChar);
    }

    for (int index = 0; index < char_count; ++index) {
        const unsigned int unicode = g_symbols.get_unicode(text_page, index);
        if (unicode == 0) {
            continue;
        }

        double left = 0.0;
        double right = 0.0;
        double bottom = 0.0;
        double top = 0.0;
        const int box_ok = g_symbols.get_char_box(text_page, index, &left, &right, &bottom, &top);
        if (box_ok == 0) {
            continue;
        }

        code_points.push_back(static_cast<jint>(unicode));
        boxes.push_back(static_cast<jfloat>(left));
        boxes.push_back(static_cast<jfloat>(right));
        boxes.push_back(static_cast<jfloat>(bottom));
        boxes.push_back(static_cast<jfloat>(top));
    }

    g_symbols.close_page(text_page);

    jclass page_class = env->FindClass("com/onyx/android/pdf/PdfiumNativeTextPage");
    if (page_class == nullptr) {
        LogError("Failed to find PdfiumNativeTextPage class");
        return nullptr;
    }
    jmethodID ctor = env->GetMethodID(page_class, "<init>", "([I[F)V");
    if (ctor == nullptr) {
        LogError("Failed to find PdfiumNativeTextPage constructor");
        return nullptr;
    }

    jintArray code_points_array = env->NewIntArray(static_cast<jsize>(code_points.size()));
    if (code_points_array == nullptr) {
        return nullptr;
    }
    if (!code_points.empty()) {
        env->SetIntArrayRegion(code_points_array, 0, static_cast<jsize>(code_points.size()), code_points.data());
    }

    jfloatArray boxes_array = env->NewFloatArray(static_cast<jsize>(boxes.size()));
    if (boxes_array == nullptr) {
        return nullptr;
    }
    if (!boxes.empty()) {
        env->SetFloatArrayRegion(boxes_array, 0, static_cast<jsize>(boxes.size()), boxes.data());
    }

    return env->NewObject(page_class, ctor, code_points_array, boxes_array);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_onyx_android_pdf_PdfiumNativeTextBridge_nativeGetPageRotation(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong page_ptr) {
    if (page_ptr == 0) {
        return 0;
    }
    if (!LoadPdfiumSymbols()) {
        return 0;
    }
    auto page = reinterpret_cast<FPDF_PAGE>(page_ptr);
    return static_cast<jint>(g_symbols.get_page_rotation(page));
}
