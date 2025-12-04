package com.tuorg.notasmultimedia.model.db

import androidx.room.TypeConverter
import java.time.LocalDateTime
import java.time.ZoneId

class Converters {
    @TypeConverter
    fun fromEpoch(millis: Long?): LocalDateTime? =
        millis?.let { LocalDateTime.ofEpochSecond(it / 1000, 0, ZoneId.systemDefault().rules.getOffset(LocalDateTime.now())) }

    @TypeConverter
    fun toEpoch(dt: LocalDateTime?): Long? =
        dt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
}
