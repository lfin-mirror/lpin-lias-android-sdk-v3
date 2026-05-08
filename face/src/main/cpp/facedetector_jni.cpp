// Copyright 2017. Electronics and Telecommunications Research Institute. All rights reserved.
// Written by Seonho Oh @ Intelligent Security Research Group

#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include "facedetector_impl_tf_android.h"

#define FACEDETECTOR_METHOD(METHOD_NAME) \
    Java_io_lpin_android_sdk_face_FaceDetector_##METHOD_NAME  // NOLINT

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jobjectArray JNICALL FACEDETECTOR_METHOD(nativeDetect)(
        JNIEnv *env, jobject thiz, jlong ptr,
        jobject bitmap);

JNIEXPORT jlong JNICALL FACEDETECTOR_METHOD(create)(
        JNIEnv *env, jobject thiz);

JNIEXPORT void JNICALL FACEDETECTOR_METHOD(destroy)(
        JNIEnv *env, jobject thiz, jlong ptr);

#ifdef __cplusplus
}
#endif

typedef struct {
    jclass cls;
    jmethodID ctorID;
    jfieldID boundingBoxID;
    jfieldID byteArrayID;
    jfieldID faceSizeID;
    jmethodID getByteArrayID;
    jmethodID getFloatArrayID;
} JNI_FACE;

void per_image_standardization(float *ptr, size_t size) {
    arma::fvec x(ptr, size, false);
    double mean = arma::mean(x);
    double stddev = arma::stddev(x);
    double adj_stddev = std::max(stddev, 1. / std::sqrt(size));
    x = (x - mean) / adj_stddev;
}

void fixed_image_standardization(float *ptr, size_t size) {
    // subtract 127.5
    // multiplied 1/128
    arma::fvec tmp(ptr, size, false);
    tmp -= 127.5f;
    tmp *= 0.0078125;
}

JNIEXPORT jlong JNICALL FACEDETECTOR_METHOD(create)(
        JNIEnv *env, jobject thiz) {
    return reinterpret_cast<jlong>(new FaceDetectorImplMTCNN);
}

JNIEXPORT void JNICALL FACEDETECTOR_METHOD(destroy)(
        JNIEnv *env, jobject thiz, jlong ptr) {
    delete reinterpret_cast<FaceDetectorImplMTCNN *>(ptr);
}

JNIEXPORT jobjectArray JNICALL FACEDETECTOR_METHOD(nativeDetect)(
        JNIEnv *env, jobject thiz, jlong ptr,
        jobject bitmap) {

    JNI_FACE Face;
    Face.cls = env->FindClass("io/lpin/android/sdk/face/Face");
    Face.ctorID = env->GetMethodID(Face.cls, "<init>", "(FFFF)V");
    Face.faceSizeID = env->GetStaticFieldID(Face.cls, "faceSize", "I");
    Face.getByteArrayID = env->GetMethodID(Face.cls, "getByteArray", "()[B");
    Face.getFloatArrayID = env->GetMethodID(Face.cls, "getFloatArray", "()[F");

    int faceSize = env->GetStaticIntField(Face.cls, Face.faceSizeID);

    AndroidBitmapInfo info;
    CHECK_EQ(AndroidBitmap_getInfo(env, bitmap, &info), ANDROID_BITMAP_RESULT_SUCCESS,
             "AndroidBitmap_getInfo");

    int input_width = info.width;
    int input_height = info.height;

    void *pixels;
    CHECK_EQ(AndroidBitmap_lockPixels(env, bitmap, &pixels),
             ANDROID_BITMAP_RESULT_SUCCESS, "AndroidBitmap_lockPixels");

    const int size = input_width * input_height * 3;
    std::vector<uint8_t> bytes;
    bytes.resize(size);

    uint8_t *dst = bytes.data();
    const int *argb = reinterpret_cast<const int *>(static_cast<const RGBA *>(pixels));
    for (uint32_t r = 0; r < input_height; r++) {
        for (uint32_t c = 0; c < input_width; c++) {
            *(dst++) = (*argb & 0xff);
            *(dst++) = ((*argb >> 8) & 0xff);
            *(dst++) = ((*argb >> 16) & 0xff);
            ++argb;
        }
    }

    // Finally, unlock the pixels
    CHECK_EQ(AndroidBitmap_unlockPixels(env, bitmap),
             ANDROID_BITMAP_RESULT_SUCCESS, "AndroidBitmap_unlockPixels");

    __android_log_print(ANDROID_LOG_INFO, TAG, "Bitmap conversion is done");

    Image img(reinterpret_cast<char *>(bytes.data()), input_width, input_height);

    auto impl = reinterpret_cast<FaceDetectorImplMTCNN *>(ptr);
    if (impl) {
        arma::fmat boundingBoxes, landmarks;
        if (impl->detect(env, thiz, img, boundingBoxes, landmarks)) {
            // convert detection results from C++ to Java
            __android_log_print(ANDROID_LOG_INFO, TAG, "Face found");
            jobjectArray arr = env->NewObjectArray(static_cast<jsize>(boundingBoxes.n_cols),
                                                   Face.cls, NULL);
            arma::vec4 bb;
            arma::mat points(5, 2);

            std::vector<uint8_t> bytes(faceSize * faceSize * 3);
            for (arma::uword c = 0; c < boundingBoxes.n_cols; c++) {
                jobject face = env->NewObject(Face.cls, Face.ctorID,
                                              boundingBoxes(0, c), boundingBoxes(1, c),
                                              boundingBoxes(2, c), boundingBoxes(3, c));

                auto biter = boundingBoxes.colptr(c);
                auto liter = landmarks.colptr(c);
                std::copy(biter, biter + 4, bb.begin());
                std::copy(liter, liter + 10, points.begin());

                align(img, bb, points, reinterpret_cast<uint8_t *>(bytes.data()), faceSize);

                jbyteArray byteValues = (jbyteArray) env->CallObjectMethod(face,
                                                                           Face.getByteArrayID);
                jbyte *buffer_ = env->GetByteArrayElements(byteValues, NULL);
                std::copy(bytes.data(), bytes.data() + bytes.size(), buffer_);
                env->ReleaseByteArrayElements(byteValues, buffer_, 0);

                jfloatArray floatValues = (jfloatArray) env->CallObjectMethod(face,
                                                                              Face.getFloatArrayID);
                jfloat *buffer = env->GetFloatArrayElements(floatValues, NULL);
                // convert byte to float
                std::copy(bytes.data(), bytes.data() + bytes.size(), buffer);

                //fixed_image_standardization(buffer, bytes.size()); // other
                // per_image_standardization(buffer, bytes.size()); // inception.resnset

                env->ReleaseFloatArrayElements(floatValues, buffer, 0);

                env->SetObjectArrayElement(arr, (int) c, face);
            }
            return arr;
        }
    }

    return env->NewObjectArray(0, Face.cls, NULL);
}
