package geo.truetune.di

import geo.truetune.data.audio.NativePitchStream
import geo.truetune.data.audio.PitchStreamBridge
import geo.truetune.domain.audio.PitchStream
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires domain interfaces to their native-backed implementations.
 *
 * The bridge is a Singleton so `System.loadLibrary` runs exactly once per
 * process. The [PitchStream] binding is also Singleton because the audio
 * callback lives inside the underlying native singleton — multiple Kotlin
 * instances would race on start/stop.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioBindingModule {
    @Binds
    @Singleton
    abstract fun bindPitchStream(impl: NativePitchStream): PitchStream
}

@Module
@InstallIn(SingletonComponent::class)
object AudioProvidersModule {
    @Provides
    @Singleton
    fun providePitchStreamBridge(): PitchStreamBridge = PitchStreamBridge()
}
