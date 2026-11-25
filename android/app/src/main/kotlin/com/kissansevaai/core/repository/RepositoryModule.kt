package com.kissansevaai.core.repository

import com.kissansevaai.core.data.DataStoreManager
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
    fun provideSchemesRepository(
        api: KissanApi,
        dao: SchemeDao,
        savedSchemeDao: SavedSchemeDao,
        syncDao: SyncOperationDao,
        dataStore: DataStoreManager,
    ): SchemesRepository = SchemesRepository(api, dao, savedSchemeDao, syncDao, dataStore)

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
    fun providePricesRepository(
        api: KissanApi,
        dao: MarketPriceDao,
        favoriteDao: MarketFavoriteDao,
        syncDao: SyncOperationDao,
        dataStore: DataStoreManager,
    ): PricesRepository = PricesRepository(api, dao, favoriteDao, syncDao, dataStore)

    @Provides
    @Singleton
    fun provideCropRepository(
        api: KissanApi,
        dao: CropAnalysisDao,
        dataStore: DataStoreManager,
    ): CropRepository = CropRepository(api, dao, dataStore)

    @Provides
    @Singleton
    fun provideChatRepository(
        api: KissanApi,
        dao: ChatDao,
        dataStore: DataStoreManager,
    ): ChatRepository = ChatRepository(api, dao, dataStore)

    @Provides
    @Singleton
    fun provideIoTRepository(api: KissanApi, dao: IoTDao): IoTRepository =
        IoTRepository(api, dao)

    @Provides
    @Singleton
    fun provideLivestockRepository(
        api: KissanApi,
        livestockDao: LivestockDao,
        dataStore: DataStoreManager,
    ): LivestockRepository = LivestockRepository(api, livestockDao, dataStore)

    @Provides
    @Singleton
    fun provideCarbonRepository(api: KissanApi): CarbonRepository =
        CarbonRepository(api)

    @Provides
    @Singleton
    fun provideWeatherRepository(
        api: KissanApi,
        weatherDao: WeatherDao,
    ): WeatherRepository = WeatherRepository(api, weatherDao)

    @Provides
    @Singleton
    fun providePlannerRepository(
        api: KissanApi,
        planDao: CropPlanDao,
        taskDao: PlannerTaskDao,
        syncDao: SyncOperationDao,
        dataStore: DataStoreManager,
    ): PlannerRepository = PlannerRepository(api, planDao, taskDao, syncDao, dataStore)
}
