package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Pinned services
    @Query("SELECT * FROM pinned_services ORDER BY pinnedAt ASC")
    fun getAllPinnedServices(): Flow<List<PinnedServiceEntity>>

    @Query("SELECT * FROM pinned_services")
    suspend fun getAllPinnedServicesList(): List<PinnedServiceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPinnedServices(entities: List<PinnedServiceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun pinService(entity: PinnedServiceEntity)

    @Query("DELETE FROM pinned_services WHERE componentKey = :componentKey")
    suspend fun unpinService(componentKey: String)

    // Groups
    @Query("SELECT * FROM app_groups ORDER BY createdAt ASC")
    fun getAllGroups(): Flow<List<AppGroupEntity>>

    @Query("SELECT * FROM app_groups")
    suspend fun getAllGroupsList(): List<AppGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateGroup(group: AppGroupEntity)

    @Query("DELETE FROM app_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("DELETE FROM group_services WHERE groupId = :groupId")
    suspend fun deleteGroupServices(groupId: String)

    @Query("SELECT * FROM group_services")
    fun getAllGroupServices(): Flow<List<GroupServiceCrossRef>>

    @Query("SELECT * FROM group_services")
    suspend fun getAllGroupServicesList(): List<GroupServiceCrossRef>

    @Query("SELECT * FROM group_services WHERE groupId = :groupId")
    fun getGroupServices(groupId: String): Flow<List<GroupServiceCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupServices(crossRefs: List<GroupServiceCrossRef>)

    @Transaction
    suspend fun saveGroupWithServices(group: AppGroupEntity, componentKeys: List<String>) {
        insertOrUpdateGroup(group)
        deleteGroupServices(group.id)
        if (componentKeys.isNotEmpty()) {
            val refs = componentKeys.map { GroupServiceCrossRef(group.id, it) }
            insertGroupServices(refs)
        }
    }

    @Transaction
    suspend fun deleteGroupAndMembers(groupId: String) {
        deleteGroupServices(groupId)
        deleteGroup(groupId)
    }
}
