package com.kissansevaai.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.kissansevaai.core.model.Scheme
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "schemes")
data class SchemeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val description: String,
    val eligibility: List<String>,
    val benefits: String = "",
    val link: String? = null,
    val phone: String? = null,
) {
    fun toDomain(): Scheme = Scheme(
        id = id,
        name = name,
        category = category,
        description = description,
        eligibility = eligibility,
        benefits = benefits,
        link = link,
        phone = phone,
    )
}

fun Scheme.toEntity(): SchemeEntity = SchemeEntity(
    id = id.ifBlank { name },
    name = name,
    category = category,
    description = description,
    eligibility = eligibility,
    benefits = benefits,
    link = link,
    phone = phone,
)

@Dao
interface SchemeDao {
    @Query("SELECT * FROM schemes ORDER BY name ASC")
    fun getAllSchemes(): Flow<List<SchemeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schemes: List<SchemeEntity>)

    @Query("SELECT COUNT(*) FROM schemes")
    suspend fun getCount(): Int

    @Query("DELETE FROM schemes")
    suspend fun clearAll()
}
