cmake_minimum_required(VERSION 3.22.1)
project(onyx_pdfium_text)

add_library(onyx_pdfium_text SHARED pdfium_text_jni.cpp)

find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(
    onyx_pdfium_text
    ${log-lib}
    ${dl-lib}
)
