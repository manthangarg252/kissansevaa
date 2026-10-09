package com.kissansevaai.core.repository

import com.kissansevaai.core.common.Resource
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.data.CatalogData
import com.kissansevaai.core.database.LoanProductDao
import com.kissansevaai.core.database.toEntity
import com.kissansevaai.core.model.LoanAdvisorRequest
import com.kissansevaai.core.model.LoanAdvisorResult
import com.kissansevaai.core.model.LoanProduct
import com.kissansevaai.core.network.KissanApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoansRepository @Inject constructor(
    private val api: KissanApi,
    private val loanProductDao: LoanProductDao,
) {
    /**
     * Emits cached loan products from Room immediately; seeds from catalog if empty.
     */
    fun getLoanProducts(): Flow<Resource<List<LoanProduct>>> = flow {
        emit(Resource.Loading)
        val count = loanProductDao.getCount()
        if (count == 0) {
            loanProductDao.insertAll(CatalogData.LOAN_PRODUCTS.map { it.toEntity() })
        }
        val cached = loanProductDao.getAllLoanProducts().first().map { it.toDomain() }
        emit(Resource.Success(cached))
    }.flowOn(Dispatchers.IO)

    /**
     * Gets personalized AI loan eligibility advice from backend.
     */
    fun getLoanAdvice(request: LoanAdvisorRequest): Flow<Resource<LoanAdvisorResult>> = flow {
        emit(Resource.Loading)
        try {
            val response = api.getLoanAdvice(request)
            emit(Resource.Success(response))
        } catch (e: Exception) {
            emit(Resource.Error(e.toUserFriendlyMessage(), e))
        }
    }.flowOn(Dispatchers.IO)
}
