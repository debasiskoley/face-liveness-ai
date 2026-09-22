package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EnrolledPersonDao {

    @Query("SELECT * FROM enrolled_persons ORDER BY timestamp DESC")
    fun getAllPersons(): Flow<List<EnrolledPersonEntity>>

    @Query("SELECT * FROM enrolled_persons WHERE id = :id LIMIT 1")
    suspend fun getPersonById(id: String): EnrolledPersonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: EnrolledPersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersons(persons: List<EnrolledPersonEntity>)

    @Update
    suspend fun updatePerson(person: EnrolledPersonEntity)

    @Query("DELETE FROM enrolled_persons WHERE id = :id")
    suspend fun deletePersonById(id: String)

    @Query("SELECT COUNT(*) FROM enrolled_persons")
    suspend fun getPersonCount(): Int
}
