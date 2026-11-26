package com.kissansevaai.core.location

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    @Singleton
    abstract fun bindReverseGeocoder(impl: AndroidReverseGeocoder): ReverseGeocoder

    @Binds
    @Singleton
    abstract fun bindLocationDetector(impl: AndroidLocationDetector): LocationDetector
}
