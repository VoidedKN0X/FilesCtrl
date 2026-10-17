cmake_minimum_required(VERSION 3.22.1)
project(filesctrl_nfs C)

# libnfs (BSD/LGPL-2.1) is fetched at build time and linked statically into one .so.
include(FetchContent)
FetchContent_Declare(
    libnfs
    GIT_REPOSITORY https://github.com/sahlberg/libnfs.git
    # Pinned to an exact commit (a tag can be moved). This is libnfs 8.0.0 plus the upstream fix that makes
    # the XDR decoder enforce size limits ("ZDR: enforce the maxsize bound"); without it a malicious server
    # could overflow fixed-size buffers such as NFSv4 file handles.
    GIT_TAG 7613236464317bee2d9636f087f995148de4db27
)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(ENABLE_TLS OFF CACHE STRING "" FORCE)
set(ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(ENABLE_UTILS OFF CACHE BOOL "" FORCE)
set(ENABLE_EXAMPLES OFF CACHE BOOL "" FORCE)
set(ENABLE_DOCUMENTATION OFF CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
FetchContent_MakeAvailable(libnfs)

add_library(filesctrl_nfs SHARED nfs_jni.c)
target_include_directories(filesctrl_nfs PRIVATE
    ${libnfs_SOURCE_DIR}/include
    ${libnfs_SOURCE_DIR}/include/nfsc
    ${libnfs_SOURCE_DIR}/mount
    ${libnfs_BINARY_DIR})
target_compile_options(filesctrl_nfs PRIVATE -Wall -Wno-unused-parameter)
target_link_libraries(filesctrl_nfs PRIVATE nfs)
if(ANDROID)
    target_link_libraries(filesctrl_nfs PRIVATE log)
endif()
# 16 KB page alignment (required for new apps on Android 15+ devices).
target_link_options(filesctrl_nfs PRIVATE "-Wl,-z,max-page-size=16384")
