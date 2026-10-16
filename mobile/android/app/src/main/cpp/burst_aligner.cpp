/**
 * DAPE Native C++ / JNI Module: Burst Optical Flow Aligner
 * 
 * Performs lightweight pyramidal Lucas-Kanade feature alignment and sharpness scoring
 * across 12 burst frames for Multi-Frame Super-Resolution (MFSR).
 */
#include <jni.h>
#include <vector>
#include <cmath>

extern "C" JNIEXPORT jdouble JNICALL
Java_id_peritanix_dape_android_core_hardware_NativeBurstAligner_computeSharpnessLaplacian(
    JNIEnv* env,
    jobject /* this */,
    jbyteArray yChannel,
    jint width,
    jint height
) {
    jbyte* y = env->GetByteArrayElements(yChannel, nullptr);

    double sumSq = 0.0;
    double sum = 0.0;
    int count = 0;

    // Laplacian kernel: [0, 1, 0; 1, -4, 1; 0, 1, 0]
    for (int r = 1; r < height - 1; r += 2) {
        for (int c = 1; c < width - 1; c += 2) {
            int center = (int)(y[r * width + c] & 0xFF);
            int top    = (int)(y[(r - 1) * width + c] & 0xFF);
            int bottom = (int)(y[(r + 1) * width + c] & 0xFF);
            int left   = (int)(y[r * width + (c - 1)] & 0xFF);
            int right  = (int)(y[r * width + (c + 1)] & 0xFF);

            double lap = (top + bottom + left + right) - 4.0 * center;
            sum += lap;
            sumSq += lap * lap;
            count++;
        }
    }

    env->ReleaseByteArrayElements(yChannel, y, JNI_ABORT);

    if (count == 0) return 0.0;
    double mean = sum / count;
    double variance = (sumSq / count) - (mean * mean);
    return variance;
}
