#include <jni.h>
#include <string>

// OpenCV headers ko include karein
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp> // Canny ke liye

// Android logging ke liye
#include <android/log.h>

// Logcat mein message dekhne ke liye ek tag
#define APPNAME "flam_C_PLUS_PLUS"

// OpenCV namespace ka istemaal karein
using namespace cv;

extern "C" {

/**
 * Yeh JNI function hai jise hum Kotlin se call karenge.
 * Yeh camera se raw image data (Y, U, V planes) leta hai.
 * Use OpenCV Mat mein convert karta hai aur process karta hai.
 */
JNIEXPORT void JNICALL
Java_com_example_flam_MainActivity_processFrame(
        JNIEnv *env,
        jobject /* this */,
        jint width,
        jint height,
        jobject y_buffer,
        jobject u_buffer,
        jobject v_buffer) {

    // 1. Raw image data (ByteBuffers) tak direct access paayein
    auto y_plane = (uint8_t *) env->GetDirectBufferAddress(y_buffer);
    auto u_plane = (uint8_t *) env->GetDirectBufferAddress(u_buffer);
    auto v_plane = (uint8_t *) env->GetDirectBufferAddress(v_buffer);

    // 2. Raw YUV data se OpenCV Mats banayein
    //    Camera humein YUV_420_888 format deta hai.
    //    Y plane (grayscale) full resolution ka hota hai.
    Mat y_mat(height, width, CV_8UC1, y_plane);

    // Hum poori YUV image banate hain, fir use Grayscale mein convert karte hain
    // Canny algorithm ke liye grayscale image chahiye
    Mat yuv_mat(height + height / 2, width, CV_8UC1, y_plane);
    Mat gray_mat;
    cvtColor(yuv_mat, gray_mat, COLOR_YUV2GRAY_NV21);

    // 3. Canny Edge Detection apply karein
    Mat edges_mat;
    Canny(gray_mat, edges_mat, 80, 100);

    // 4. Logcat mein ek message print karein yeh dikhane ke liye ki kaam ho gaya
    //    Hum abhi image ko wapas nahi bhej rahe hain (woh Phase 4 mein karenge).
    __android_log_print(ANDROID_LOG_DEBUG, APPNAME, "Canny edge detection complete. Edges found: %d", countNonZero(edges_mat));
}


/**
 * Yeh template ka default function hai.
 * Ise yahin rehne dete hain taaki code break na ho.
 */
JNIEXPORT jstring JNICALL
Java_com_example_flam_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

} // extern "C"