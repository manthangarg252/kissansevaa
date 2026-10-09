package com.kissansevaai.core.repository

import com.kissansevaai.core.database.*
import com.kissansevaai.core.network.KissanApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSchemesRepository(api: KissanApi, dao: SchemeDao): SchemesRepository =
        SchemesRepository(api, dao)

    @Provides
    @Singleton
    fun provideLoansRepository(api: KissanApi, dao: LoanProductDao): LoansRepository =
        LoansRepository(api, dao)

    @Provides
    @Singleton
    fun provideTradersRepository(api: KissanApi, dao: TraderDao): TradersRepository =
        TradersRepository(api, dao)

    @Provides
    @Singleton
    fun providePricesRepository(api: KissanApi, dao: MarketPriceDao): PricesRepository =
        PricesRepository(api, dao)

    @Provides
    @Singleton
    fun provideCropRepository(api: KissanApi, dao: CropAnalysisDao): CropRepository =
        CropRepository(api, dao)

    @Provides
    @Singleton
    fun provideChatRepository(api: KissanApi, dao: ChatDao): ChatRepository =
        ChatRepository(api, dao)

    @Provides
    @Singleton
    fun provideIoTRepository(api: KissanApi, dao: IoTDao): IoTRepository =
        IoTRepository(api, dao)

    @Provides
    @Singleton
    fun provideLivestockRepository(api: KissanApi): LivestockRepository =
        LivestockRepository(api)

    @Provides
    @Singleton
    fun provideCarbonRepository(api: KissanApi): CarbonRepository =
        CarbonRepository(api)
}
