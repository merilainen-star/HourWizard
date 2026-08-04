package com.aistudio.tuntivelho.leimaus.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stamp_logs")
data class StampEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val formattedTime: String,
    val actionType: String, // "SISÄÄN" or "ULOS"
    val isSuccess: Boolean,
    val message: String,
    val balance: String = "", // e.g. "+0:12" or "-0:27"
    val rawDetails: String = "" // Raw API/JSON or debug log info
)
