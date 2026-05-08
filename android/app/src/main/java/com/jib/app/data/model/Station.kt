package com.jib.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "stations")
data class Station(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val connectorTypes: String, // JSON-encoded List<String> — Room needs flat types
    val powerKw: Double?,
    val networkOperator: String?,
    val isAvailable: Boolean,
    val address: String? = null,
) {
    fun connectorTypeList(): List<String> =
        Gson().fromJson(connectorTypes, object : TypeToken<List<String>>() {}.type)
}
