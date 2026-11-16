package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.ActivityLogEntity
import com.example.data.model.BodyCompositionEntity
import com.example.data.model.ClientEntity
import com.example.data.model.ClientTaskScheduleEntity
import com.example.data.model.MealPlanNoteEntity
import com.example.data.model.MeasurementEntity
import com.example.data.model.PackageRuleEntity
import com.example.data.model.ProgressPhotoEntity
import com.example.data.model.RescheduleRequestEntity
import com.example.data.model.SessionEntity
import com.example.data.model.WorkoutExerciseEntity
import com.example.data.model.WorkoutRequestEntity

@Database(
    entities = [
        PackageRuleEntity::class,
        ClientEntity::class,
        MeasurementEntity::class,
        BodyCompositionEntity::class,
        ProgressPhotoEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutRequestEntity::class,
        SessionEntity::class,
        RescheduleRequestEntity::class,
        ClientTaskScheduleEntity::class,
        MealPlanNoteEntity::class,
        ActivityLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class IhcDatabase : RoomDatabase() {
    abstract fun ihcDao(): IhcDao

    companion object {
        @Volatile
        private var INSTANCE: IhcDatabase? = null

        fun getInstance(context: Context): IhcDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    IhcDatabase::class.java,
                    "ihc_management.db"
                ).fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
