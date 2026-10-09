package com.kissansevaai.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.kissansevaai.core.model.LoanProduct
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "loan_products")
data class LoanProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val description: String,
    val benefits: List<String>,
    val eligibility: List<String>,
    val link: String? = null,
    val provider: String = "",
    val interestRate: String = "",
    val maxAmount: String = "",
    val tenure: String = "",
) {
    fun toDomain(): LoanProduct = LoanProduct(
        id = id,
        name = name,
        type = type,
        description = description,
        benefits = benefits,
        eligibility = eligibility,
        link = link,
        provider = provider,
        interestRate = interestRate,
        maxAmount = maxAmount,
        tenure = tenure,
    )
}

fun LoanProduct.toEntity(): LoanProductEntity = LoanProductEntity(
    id = id.ifBlank { name },
    name = name,
    type = type,
    description = description,
    benefits = benefits,
    eligibility = eligibility,
    link = link,
    provider = provider,
    interestRate = interestRate,
    maxAmount = maxAmount,
    tenure = tenure,
)

@Dao
interface LoanProductDao {
    @Query("SELECT * FROM loan_products ORDER BY name ASC")
    fun getAllLoanProducts(): Flow<List<LoanProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(loans: List<LoanProductEntity>)

    @Query("SELECT COUNT(*) FROM loan_products")
    suspend fun getCount(): Int

    @Query("DELETE FROM loan_products")
    suspend fun clearAll()
}
