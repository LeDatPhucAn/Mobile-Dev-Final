package com.example.mobile_image_retrieval.data.db

import androidx.room.TypeConverter
import com.example.mobile_image_retrieval.domain.model.MediaType

class Converters {
    @TypeConverter fun mediaTypeToString(value: MediaType): String = value.name
    @TypeConverter fun stringToMediaType(value: String): MediaType = MediaType.valueOf(value)
}
