//
// JNI entry points for the AudioInput singleton.
//
// The class exists on the C++ side; Kotlin never sees it directly. It talks
// through this file's `nativeXxx` functions, which are bound to
// `PitchStreamBridge` on the Kotlin side.
//
// A single mutex serializes start/stop lifecycle. It is NEVER touched by the
// Oboe callback or the detector worker — both of those talk to atomics only.
//
#include <jni.h>
#include <memory>
#include <mutex>

#include "AudioInput.h"

namespace {
    std::unique_ptr<AudioInput> g_input;
    std::mutex                  g_lifecycle;

    AudioInput& ensure() {
        if (!g_input) g_input = std::make_unique<AudioInput>();
        return *g_input;
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_geo_truetune_data_audio_PitchStreamBridge_nativeStart(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_lifecycle);
    return ensure().start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_geo_truetune_data_audio_PitchStreamBridge_nativeStop(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_lifecycle);
    if (g_input) g_input->stop();
}

JNIEXPORT jboolean JNICALL
Java_geo_truetune_data_audio_PitchStreamBridge_nativeIsRunning(JNIEnv*, jobject) {
    // Read-only — a stale answer here is harmless.
    return (g_input && g_input->isRunning()) ? JNI_TRUE : JNI_FALSE;
}

// The UI polls these at ~30 Hz. Each getter is a single atomic load — cheap
// even at that rate.
JNIEXPORT jfloat JNICALL
Java_geo_truetune_data_audio_PitchStreamBridge_nativeGetFrequency(JNIEnv*, jobject) {
    return g_input ? g_input->lastFrequency() : -1.0f;
}

JNIEXPORT jfloat JNICALL
Java_geo_truetune_data_audio_PitchStreamBridge_nativeGetClarity(JNIEnv*, jobject) {
    return g_input ? g_input->lastClarity() : 0.0f;
}

JNIEXPORT jfloat JNICALL
Java_geo_truetune_data_audio_PitchStreamBridge_nativeGetRmsDb(JNIEnv*, jobject) {
    return g_input ? g_input->lastRmsDb() : -120.0f;
}

JNIEXPORT jint JNICALL
Java_geo_truetune_data_audio_PitchStreamBridge_nativeGetSampleRate(JNIEnv*, jobject) {
    return g_input ? g_input->sampleRate() : 0;
}

JNIEXPORT jint JNICALL
Java_geo_truetune_data_audio_PitchStreamBridge_nativeGetFramesPerBurst(JNIEnv*, jobject) {
    return g_input ? g_input->framesPerBurst() : 0;
}

JNIEXPORT jint JNICALL
Java_geo_truetune_data_audio_PitchStreamBridge_nativeGetBufferSize(JNIEnv*, jobject) {
    return g_input ? g_input->bufferSize() : 0;
}

JNIEXPORT jint JNICALL
Java_geo_truetune_data_audio_PitchStreamBridge_nativeGetXRuns(JNIEnv*, jobject) {
    return g_input ? g_input->xruns() : 0;
}

JNIEXPORT jint JNICALL
Java_geo_truetune_data_audio_PitchStreamBridge_nativeGetApi(JNIEnv*, jobject) {
    return g_input ? g_input->apiUsed() : 0;
}

JNIEXPORT jboolean JNICALL
Java_geo_truetune_data_audio_PitchStreamBridge_nativeIsLowLatency(JNIEnv*, jobject) {
    return (g_input && g_input->lowLatencyPath()) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
