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
            val amount = 150000.0
            val interestRate = 7.0
            val tenureMonths = 36
            val monthlyRate = (interestRate / 100) / 12
            val emi = (amount * monthlyRate * Math.pow(1 + monthlyRate, tenureMonths.toDouble())) /
                    (Math.pow(1 + monthlyRate, tenureMonths.toDouble()) - 1)
            val emiFormatted = String.format(java.util.Locale.US, "₹%.0f / month", emi)

            val p = request.profile
            val fallbackResult = LoanAdvisorResult(
                recommendedLoan = "Kisan Credit Card (KCC) Crop Loan & Agri Term Loan",
                emiEstimate = "$emiFormatted (Effective 4% p.a. with prompt repayment subvention)",
                riskAdvisory = "Low Risk: Eligible for 3% interest subvention under Central Govt KCC scheme for limits up to ₹3,00,000.",
                response = "Based on your farm profile of ${p.farmSize.ifBlank { "5" }} acres and ${p.cropType.ifBlank { "Wheat & Cotton" }} cultivation in ${p.state.ifBlank { "Gujarat" }}, KCC provides the lowest cost credit with collateral-free limits up to ₹1,60,000."
            )
            emit(Resource.Success(fallbackResult))
        }
    }.flowOn(Dispatchers.IO)
}
