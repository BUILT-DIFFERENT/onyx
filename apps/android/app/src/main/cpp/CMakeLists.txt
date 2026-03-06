cmake_minimum_required(VERSION 3.22.1)
project(onyx_android_native)

add_library(onyx_pdfium_text SHARED pdfium_text_jni.cpp)
add_library(onyx_vk_ink SHARED vk_ink_renderer_jni.cpp)

find_library(log-lib log)
find_library(dl-lib dl)
find_library(vulkan-lib vulkan)

if(NOT vulkan-lib)
    message(FATAL_ERROR "Vulkan library not found")
endif()

target_link_libraries(
    onyx_pdfium_text
    ${log-lib}
    ${dl-lib}
)

target_link_libraries(
    onyx_vk_ink
    ${log-lib}
    ${dl-lib}
    ${vulkan-lib}
)
