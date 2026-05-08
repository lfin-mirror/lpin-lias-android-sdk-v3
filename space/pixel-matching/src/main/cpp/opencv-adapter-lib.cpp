#include <jni.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "jnilog"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG , LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO , LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN , LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR , LOG_TAG, __VA_ARGS__)

#include "common_pleaseModifyThisWhenPorting.hpp"

char *jbyteArray2cstr_(JNIEnv *pJNIEnv, jbyteArray javaBytes, int *length);

char *jbyteArray2cstr_(JNIEnv *pJNIEnv, jbyteArray javaBytes, int *length) {
    size_t len = pJNIEnv->GetArrayLength(javaBytes);
    *length = (int) len;

    jbyte *nativeBytes = pJNIEnv->GetByteArrayElements(javaBytes, 0);

    char *nativeStr = lpin::GetPtrOfBuffer();
    memcpy(nativeStr, (const char *) nativeBytes, len);
    pJNIEnv->ReleaseByteArrayElements(javaBytes, nativeBytes, JNI_ABORT);

    return nativeStr;
}

extern "C"
JNIEXPORT jint

JNICALL
Java_io_lpin_android_sdk_space_pixel_matching_OpenCVAdaptor_initialize(JNIEnv *env, jobject thiz,
                                                                      jdouble wrapper_version) {
    return lpin::Initialize(wrapper_version);
}

extern "C"
JNIEXPORT jint

JNICALL
Java_io_lpin_android_sdk_space_pixel_matching_OpenCVAdaptor_setMarker(JNIEnv *env, jobject thiz,
                                                                     jbyteArray bitmap,
                                                                     jint width, jint height) {
    char *addr = NULL;
    int aLength = 0;
    int ret;
    addr = jbyteArray2cstr_(env, bitmap, &aLength);
    LOGV("setMarker start");
    LOGV("bitmap length : %d", aLength);
    LOGV("bitmap width: %d, height: %d", width, height);

    ret = lpin::SetMarker(addr, width, height);

    LOGV("setMarker end");
//    if (addr != NULL) {
//        free((void *) addr);
//        addr = NULL;
//    }
    return ret;
}

extern "C"
JNIEXPORT jint

JNICALL
Java_io_lpin_android_sdk_space_pixel_matching_OpenCVAdaptor_startRetriveTargetData(JNIEnv *env,
                                                                                  jobject thiz) {
    return lpin::Start_RetriveTargetData();
}

extern "C"
JNIEXPORT jint

JNICALL
Java_io_lpin_android_sdk_space_pixel_matching_OpenCVAdaptor_endRetriveTargetData(JNIEnv *env,
                                                                                jobject thiz) {
    return lpin::End_RetriveTargetData();
}

extern "C"
JNIEXPORT jint

JNICALL
Java_io_lpin_android_sdk_space_pixel_matching_OpenCVAdaptor_startProcessQuery(JNIEnv *env,
                                                                             jobject thiz) {
    return lpin::Start_ProcessQuery();
}

extern "C"
JNIEXPORT jint

JNICALL
Java_io_lpin_android_sdk_space_pixel_matching_OpenCVAdaptor_endProcessQuery(JNIEnv *env,
                                                                           jobject thiz) {
    return lpin::End_ProcessQuery();
}

extern "C"
JNIEXPORT jdouble

JNICALL
Java_io_lpin_android_sdk_space_pixel_matching_OpenCVAdaptor_getConfidenceRate(JNIEnv *env,
                                                                             jobject thiz) {
    return lpin::GetConfidenceRate();
}

extern "C"
JNIEXPORT jint

JNICALL
Java_io_lpin_android_sdk_space_pixel_matching_OpenCVAdaptor_process(JNIEnv *env, jobject thiz,
                                                                   jbyteArray bitmap, jint width,
                                                                   jint height) {

    char *addr = NULL;
    int aLength = 0;
    int ret;
    addr = jbyteArray2cstr_(env, bitmap, &aLength);
    LOGV("Process start");

    ret = lpin::Process(width, height);

    LOGV("Process end");

    return ret;
}