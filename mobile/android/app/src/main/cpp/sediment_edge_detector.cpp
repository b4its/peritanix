/**
 * DAPE Native C++ / JNI Module: Stokes Sedimentation Edge Detector
 * 
 * Analyzes sedimentation jar images to locate the distinct horizontal boundaries
 * separating sand (settled @ 40s), silt (settled @ 2h), and clay suspension layers.
 */
#include <jni.h>
#include <vector>
#include <cmath>
#include <algorithm>

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_id_peritanix_dape_android_core_physics_NativeSedimentDetector_detectLayers(
    JNIEnv* env,
    jobject /* this */,
    jbyteArray nv21Bytes,
    jint width,
    jint height
) {
    jbyte* yuv = env->GetByteArrayElements(nv21Bytes, nullptr);

    // Compute horizontal edge gradients along the vertical axis of the jar
    std::vector<double> verticalGradient(height, 0.0);
    int startX = width * 0.25;
    int endX = width * 0.75;

    for (int y = 1; y < height - 1; ++y) {
        double rowGrad = 0.0;
        for (int x = startX; x < endX; ++x) {
            int top = (int)(yuv[(y - 1) * width + x] & 0xFF);
            int bottom = (int)(yuv[(y + 1) * width + x] & 0xFF);
            rowGrad += std::abs(bottom - top);
        }
        verticalGradient[y] = rowGrad / (endX - startX);
    }

    env->ReleaseByteArrayElements(nv21Bytes, yuv, JNI_ABORT);

    // Identify top two gradient peaks corresponding to silt-clay and sand-silt interfaces
    int peak1 = height * 0.4;
    int peak2 = height * 0.75;
    double max1 = 0, max2 = 0;

    for (int y = (int)(height * 0.2); y < (int)(height * 0.55); ++y) {
        if (verticalGradient[y] > max1) { max1 = verticalGradient[y]; peak1 = y; }
    }
    for (int y = (int)(height * 0.55); y < (int)(height * 0.9); ++y) {
        if (verticalGradient[y] > max2) { max2 = verticalGradient[y]; peak2 = y; }
    }

    double sandThickness = height * 0.9 - peak2;
    double siltThickness = peak2 - peak1;
    double clayThickness = peak1 - height * 0.2;

    double total = sandThickness + siltThickness + clayThickness;
    if (total <= 0) total = 1.0;

    jdouble result[3];
    result[0] = (sandThickness / total) * 100.0; // Sand %
    result[1] = (siltThickness / total) * 100.0; // Silt %
    result[2] = (clayThickness / total) * 100.0; // Clay %

    jdoubleArray out = env->NewDoubleArray(3);
    env->SetDoubleArrayRegion(out, 0, 3, result);
    return out;
}
