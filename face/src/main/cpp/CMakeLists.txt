# Sets the minimum version of CMake required to build the native
# library. You should either keep the default value or only pass a
# value of 3.4.0 or lower.

cmake_minimum_required(VERSION 3.4.1)

set(LPIN_16KB_PAGE_SIZE_LINK_FLAGS
        "-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds it for you.
# Gradle automatically packages shared libraries with your APK.

include_directories(
        third_party/dlib-19.16
        # third_party/armadillo-10.3.0/include)
        third_party/armadillo-9.100.5/include)

add_library( # Sets the name of the library.
        facedetector

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        # Associated headers in the same location as their source
        # file are automatically included.
        dlib_raw_rgb_image.cpp
        mtcnn.cpp
        facedetector_jni.cpp)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in the
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        facedetector
        android
        jnigraphics
        log
        m
        z)

set_target_properties(facedetector PROPERTIES
        LINK_FLAGS "${LPIN_16KB_PAGE_SIZE_LINK_FLAGS}")

if (ANDROID_ABI MATCHES "^armeabi-v7a$")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfloat-abi=softfp -mfpu=neon")
elseif (ANDROID_ABI MATCHES "^arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -ftree-vectorize")
endif ()

#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} \
#                    -std=gnu++14 -fno-exceptions -fno-rtti -O2 -Wno-narrowing \
#                    -fPIE")
#set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} \
#                              -Wl,--allow-multiple-definition \
#                              -Wl,--whole-archive -fPIE -v")

add_library( # Sets the name of the library.
        external
        # Sets the library as a shared library.
        SHARED
        # Provides a relative path to your source file(s).
        # Associated headers in the same location as their source
        imageutils_jni.cc
        rgb2yuv.cc
        yuv2rgb.cc)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in the
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        external
        android
        log
        jnigraphics
        m
        atomic
        z)

set_target_properties(external PROPERTIES
        LINK_FLAGS "${LPIN_16KB_PAGE_SIZE_LINK_FLAGS}")
