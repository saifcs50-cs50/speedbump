package com.speedbump.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.speedbump.core.data.db.entity.AppRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {

    @Upsert
    suspend fun upsert(rule: AppRuleEntity)

    @Upsert
    suspend fun upsertAll(rules: List<AppRuleEntity>)

    @Delete
    suspend fun delete(rule: AppRuleEntity)

    @Query("DELETE FROM app_rules WHERE package_name = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("SELECT * FROM app_rules ORDER BY package_name")
    fun observeAll(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE enabled = 1")
    fun observeEnabled(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE package_name = :packageName")
    fun observe(packageName: String): Flow<AppRuleEntity?>

    @Query("SELECT * FROM app_rules WHERE package_name = :packageName")
    suspend fun find(packageName: String): AppRuleEntity?

    @Query("SELECT COUNT(*) FROM app_rules WHERE enabled = 1")
    fun observeEnabledCount(): Flow<Int>

    @Query("UPDATE app_rules SET enabled = :enabled WHERE package_name = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)
}
