package geo.truetune.data.audio

/**
 * Thin JNI façade. Every function here has a matching `Java_geo_truetune_data_
 * audio_PitchStreamBridge_nativeXxx` symbol in `native-lib.cpp`. Nothing else
 * in the codebase calls JNI directly — this class is the only bridge.
 *
 * The system library is loaded exactly once per process from the companion
 * object. Loading it later (e.g. lazily on `start`) risks a subtle race with
 * Hilt-injected multi-thread callers.
 */
class PitchStreamBridge {

    external fun nativeStart(): Boolean
    external fun nativeStop()
    external fun nativeIsRunning(): Boolean

    external fun nativeGetFrequency(): Float
    external fun nativeGetClarity(): Float
    external fun nativeGetRmsDb(): Float
    external fun nativeGetSampleRate(): Int
    external fun nativeGetFramesPerBurst(): Int
    external fun nativeGetBufferSize(): Int
    external fun nativeGetXRuns(): Int
    external fun nativeGetApi(): Int
    external fun nativeIsLowLatency(): Boolean

    companion object {
        init {
            System.loadLibrary("truetune_audio")
        }
    }
}
