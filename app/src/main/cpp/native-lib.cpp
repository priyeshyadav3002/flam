#include <jni.h>
#include <string>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <android/log.h>

#define APPNAME "flam_C_PLUS_PLUS"

using namespace cv;

extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_com_example_flam_MainActivity_processFrame(
        JNIEnv *env,
        jobject,
        jint width,
        jint height,
        jobject y_buffer,
        jobject,
        jobject) {

    // 1. Y-plane (grayscale data) tak direct access paayein
    auto y_plane = (uint8_t *) env->GetDirectBufferAddress(y_buffer);

    // 2. Y-plane ko seedha ek grayscale Mat banayein
    //    Camera ka Y-plane hi humari grayscale image hai.
    Mat gray_mat(height, width, CV_8UC1, y_plane);

    // 3. Canny Edge Detection
    Mat edges_mat;
    Canny(gray_mat, edges_mat, 80, 100);

    // 4. Processed data (edges) ko Kotlin ko return karein
    int dataSize = width * height;
    jbyteArray outputArray = env->NewByteArray(dataSize);

    env->SetByteArrayRegion(outputArray, 0, dataSize, (jbyte *) edges_mat.data);

    return outputArray;
}


JNIEXPORT jstring JNICALL
Java_com_example_flam_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

}