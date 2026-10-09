package com.kissansevaai.core.ai

import com.kissansevaai.core.ai.ondevice.AndroidSpeechToText
import com.kissansevaai.core.ai.ondevice.AndroidTextToSpeech
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindSpeechToText(impl: AndroidSpeechToText): SpeechToText

    @Binds
    @Singleton
    abstract fun bindTextToSpeech(impl: AndroidTextToSpeech): TextToSpeechEngine

    @Binds
    @Singleton
    abstract fun bindFieldExtractor(impl: com.kissansevaai.core.ai.remote.RemoteFieldExtractor): FieldExtractor
}
