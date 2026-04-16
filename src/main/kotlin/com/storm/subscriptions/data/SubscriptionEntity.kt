package io.github.folk97stormi.subtrack.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "priceRubles") val priceUsd: Double,
    val billingPeriod: BillingPeriod,
    val nextBillingEpochDay: Long
)

enum class BillingPeriod {
    MONTHLY,
    YEARLY
}
