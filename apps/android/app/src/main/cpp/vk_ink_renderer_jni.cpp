#include <jni.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <vulkan/vulkan.h>

#include <algorithm>
#include <cstdint>
#include <mutex>
#include <string>

namespace {
constexpr const char* kTag = "VkInkRendererJni";

void LogError(const std::string& message) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "%s", message.c_str());
}

class VulkanInkRenderer {
public:
    VulkanInkRenderer() = default;

    ~VulkanInkRenderer() {
        std::scoped_lock<std::mutex> lock(mutex_);
        DestroySurfaceLocked();
        if (instance_ != VK_NULL_HANDLE) {
            vkDestroyInstance(instance_, nullptr);
            instance_ = VK_NULL_HANDLE;
        }
    }

    bool InitSurface(JNIEnv* env, jobject surface) {
        std::scoped_lock<std::mutex> lock(mutex_);
        if (!EnsureInstanceLocked()) {
            return false;
        }

        DestroySurfaceLocked();

        if (surface == nullptr) {
            LogError("InitSurface received null surface");
            return false;
        }

        native_window_ = ANativeWindow_fromSurface(env, surface);
        if (native_window_ == nullptr) {
            LogError("ANativeWindow_fromSurface failed");
            return false;
        }

        VkAndroidSurfaceCreateInfoKHR create_info{};
        create_info.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
        create_info.window = native_window_;

        VkResult surface_result = vkCreateAndroidSurfaceKHR(instance_, &create_info, nullptr, &surface_);
        if (surface_result != VK_SUCCESS) {
            LogError("vkCreateAndroidSurfaceKHR failed");
            DestroySurfaceLocked();
            return false;
        }

        initialized_ = true;
        return true;
    }

    void DestroySurface() {
        std::scoped_lock<std::mutex> lock(mutex_);
        DestroySurfaceLocked();
    }

    void Resize(uint32_t width, uint32_t height) {
        std::scoped_lock<std::mutex> lock(mutex_);
        width_ = std::max(1u, width);
        height_ = std::max(1u, height);
    }

    bool DrawFrame() {
        std::scoped_lock<std::mutex> lock(mutex_);
        return initialized_ && instance_ != VK_NULL_HANDLE && surface_ != VK_NULL_HANDLE;
    }

    void SetPageSize(float page_width, float page_height) {
        std::scoped_lock<std::mutex> lock(mutex_);
        page_width_ = page_width;
        page_height_ = page_height;
    }

    void SetViewTransform(float zoom, float pan_x, float pan_y) {
        std::scoped_lock<std::mutex> lock(mutex_);
        zoom_ = zoom;
        pan_x_ = pan_x;
        pan_y_ = pan_y;
    }

private:
    bool EnsureInstanceLocked() {
        if (instance_ != VK_NULL_HANDLE) {
            return true;
        }

        const char* extensions[] = {
            VK_KHR_SURFACE_EXTENSION_NAME,
            VK_KHR_ANDROID_SURFACE_EXTENSION_NAME,
        };

        VkApplicationInfo app_info{};
        app_info.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        app_info.pApplicationName = "OnyxVkInkRenderer";
        app_info.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
        app_info.pEngineName = "OnyxVkInkEngine";
        app_info.engineVersion = VK_MAKE_VERSION(1, 0, 0);
        app_info.apiVersion = VK_API_VERSION_1_0;

        VkInstanceCreateInfo create_info{};
        create_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        create_info.pApplicationInfo = &app_info;
        create_info.enabledExtensionCount = 2;
        create_info.ppEnabledExtensionNames = extensions;

        VkResult result = vkCreateInstance(&create_info, nullptr, &instance_);
        if (result != VK_SUCCESS) {
            LogError("vkCreateInstance failed");
            instance_ = VK_NULL_HANDLE;
            return false;
        }

        return true;
    }

    void DestroySurfaceLocked() {
        if (surface_ != VK_NULL_HANDLE && instance_ != VK_NULL_HANDLE) {
            vkDestroySurfaceKHR(instance_, surface_, nullptr);
            surface_ = VK_NULL_HANDLE;
        }
        if (native_window_ != nullptr) {
            ANativeWindow_release(native_window_);
            native_window_ = nullptr;
        }
        initialized_ = false;
    }

    std::mutex mutex_;
    VkInstance instance_ = VK_NULL_HANDLE;
    VkSurfaceKHR surface_ = VK_NULL_HANDLE;
    ANativeWindow* native_window_ = nullptr;

    uint32_t width_ = 1;
    uint32_t height_ = 1;
    float page_width_ = 0.0f;
    float page_height_ = 0.0f;
    float zoom_ = 1.0f;
    float pan_x_ = 0.0f;
    float pan_y_ = 0.0f;
    bool initialized_ = false;
};

VulkanInkRenderer* AsRenderer(jlong handle) {
    return reinterpret_cast<VulkanInkRenderer*>(handle);
}
}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeIsSupported(JNIEnv* /*env*/, jclass /*clazz*/) {
    uint32_t instance_version = 0;
    return vkEnumerateInstanceVersion(&instance_version) == VK_SUCCESS ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeCreateRenderer(JNIEnv* /*env*/, jclass /*clazz*/) {
    return reinterpret_cast<jlong>(new VulkanInkRenderer());
}

extern "C" JNIEXPORT void JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeDestroyRenderer(JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {
    auto* renderer = AsRenderer(handle);
    if (renderer == nullptr) {
        return;
    }
    delete renderer;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeInitSurface(
    JNIEnv* env,
    jclass /*clazz*/,
    jlong handle,
    jobject surface,
    jint /*width*/,
    jint /*height*/) {
    auto* renderer = AsRenderer(handle);
    if (renderer == nullptr) {
        return JNI_FALSE;
    }
    return renderer->InitSurface(env, surface) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeDestroySurface(JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {
    auto* renderer = AsRenderer(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->DestroySurface();
}

extern "C" JNIEXPORT void JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeResize(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong handle,
    jint width,
    jint height) {
    auto* renderer = AsRenderer(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->Resize(static_cast<uint32_t>(std::max(1, width)), static_cast<uint32_t>(std::max(1, height)));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeDrawFrame(JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {
    auto* renderer = AsRenderer(handle);
    if (renderer == nullptr) {
        return JNI_FALSE;
    }
    return renderer->DrawFrame() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeSetPageSize(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong handle,
    jfloat page_width,
    jfloat page_height) {
    auto* renderer = AsRenderer(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->SetPageSize(page_width, page_height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeSetViewTransform(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong handle,
    jfloat zoom,
    jfloat pan_x,
    jfloat pan_y) {
    auto* renderer = AsRenderer(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->SetViewTransform(zoom, pan_x, pan_y);
}

extern "C" JNIEXPORT void JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeSetOverlayState(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong /*handle*/,
    jint /*selected_stroke_count*/,
    jint /*lasso_point_count*/,
    jboolean /*hover_visible*/,
    jfloat /*hover_x*/,
    jfloat /*hover_y*/,
    jfloat /*hover_radius*/,
    jint /*hover_color*/,
    jfloat /*hover_alpha*/) {}

extern "C" JNIEXPORT void JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeSyncCommittedStrokes(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong /*handle*/, jint /*stroke_count*/) {}

extern "C" JNIEXPORT void JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeStartStroke(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong /*handle*/,
    jlong /*stroke_id*/,
    jfloat /*x*/,
    jfloat /*y*/,
    jlong /*event_time_millis*/,
    jfloat /*pressure*/,
    jfloat /*tilt_radians*/,
    jfloat /*orientation_radians*/,
    jint /*argb_color*/,
    jfloat /*alpha_multiplier*/,
    jfloat /*base_width*/,
    jfloat /*min_width_factor*/,
    jfloat /*max_width_factor*/,
    jfloat /*smoothing_level*/,
    jfloat /*end_taper_strength*/,
    jint /*line_style_ordinal*/,
    jint /*tool_ordinal*/) {}

extern "C" JNIEXPORT void JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeAddStrokePoint(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong /*handle*/,
    jlong /*stroke_id*/,
    jfloat /*x*/,
    jfloat /*y*/,
    jlong /*event_time_millis*/,
    jfloat /*pressure*/,
    jfloat /*tilt_radians*/,
    jfloat /*orientation_radians*/) {}

extern "C" JNIEXPORT void JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeFinishStroke(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong /*handle*/,
    jlong /*stroke_id*/,
    jfloat /*x*/,
    jfloat /*y*/,
    jlong /*event_time_millis*/,
    jfloat /*pressure*/,
    jfloat /*tilt_radians*/,
    jfloat /*orientation_radians*/) {}

extern "C" JNIEXPORT void JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeCancelStroke(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong /*handle*/, jlong /*stroke_id*/) {}

extern "C" JNIEXPORT void JNICALL
Java_com_onyx_android_ink_vk_VkNativeBridge_nativeRemoveFinishedStrokes(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong /*handle*/, jlongArray /*stroke_ids*/) {}
