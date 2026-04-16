package io.github.folk97stormi.subtrack.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.folk97stormi.subtrack.billing.PremiumBillingManager
import io.github.folk97stormi.subtrack.data.BillingPeriod
import io.github.folk97stormi.subtrack.data.SubscriptionEntity
import io.github.folk97stormi.subtrack.data.SubscriptionRepository
import io.github.folk97stormi.subtrack.reminder.ReminderScheduler
import io.github.folk97stormi.subtrack.util.AppLogger
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val FREE_LIMIT = 3

data class SubscriptionUiModel(
    val id: Long,
    val name: String,
    val priceUsd: Double,
    val billingPeriod: BillingPeriod,
    val nextBillingDate: LocalDate
)

data class SubscriptionScreenState(
    val items: List<SubscriptionUiModel> = emptyList(),
    val monthlyTotal: Double = 0.0,
    val yearlyTotal: Double = 0.0,
    val isPremium: Boolean = false,
    val canAddMore: Boolean = true,
    val isBillingLoading: Boolean = true,
    val isBillingAvailable: Boolean = false,
    val canPurchasePremium: Boolean = false,
    val hasPendingPurchase: Boolean = false,
    val premiumPriceLabel: String? = null
)

data class SubscriptionFormState(
    val id: Long? = null,
    val name: String = "",
    val price: String = "",
    val billingPeriod: BillingPeriod = BillingPeriod.MONTHLY,
    val nextBillingDate: LocalDate = LocalDate.now(),
    val showNameSuggestions: Boolean = false
)

class SubscriptionViewModel(
    private val repository: SubscriptionRepository,
    private val reminderScheduler: ReminderScheduler,
    private val premiumBillingManager: PremiumBillingManager
) : ViewModel() {

    val billingMessages: SharedFlow<String> = premiumBillingManager.events

    val screenState: StateFlow<SubscriptionScreenState> = combine(
        repository.observeAll(),
        premiumBillingManager.state
    ) { entities, premiumState ->
        val items = entities.map {
            SubscriptionUiModel(
                id = it.id,
                name = it.name,
                priceUsd = it.priceUsd,
                billingPeriod = it.billingPeriod,
                nextBillingDate = LocalDate.ofEpochDay(it.nextBillingEpochDay)
            )
        }
        SubscriptionScreenState(
            items = items,
            monthlyTotal = entities.sumOf {
                if (it.billingPeriod == BillingPeriod.MONTHLY) it.priceUsd else it.priceUsd / 12.0
            },
            yearlyTotal = entities.sumOf {
                if (it.billingPeriod == BillingPeriod.MONTHLY) it.priceUsd * 12.0 else it.priceUsd
            },
            isPremium = premiumState.isPremium,
            canAddMore = premiumState.isPremium || items.size < FREE_LIMIT,
            isBillingLoading = premiumState.isLoading,
            isBillingAvailable = premiumState.isBillingAvailable,
            canPurchasePremium = premiumState.canPurchase,
            hasPendingPurchase = premiumState.hasPendingPurchase,
            premiumPriceLabel = premiumState.priceLabel
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionScreenState())

    init {
        viewModelScope.launch {
            normalizeExpiredSubscriptions()
        }
        viewModelScope.launch {
            premiumBillingManager.state.collect { premiumState ->
                syncReminders(premiumState.isPremium)
            }
        }
    }

    fun saveSubscription(
        form: SubscriptionFormState,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val name = form.name.trim()
        if (name.isBlank()) {
            onError("Enter a subscription name")
            return
        }

        val price = form.price.replace(',', '.').toDoubleOrNull()
        if (price == null || price <= 0.0) {
            onError("Price must be greater than $0")
            return
        }

        if (form.id == null && !screenState.value.canAddMore) {
            onError("The free plan allows up to 3 subscriptions")
            return
        }

        viewModelScope.launch {
            runCatching {
                val entity = SubscriptionEntity(
                    id = form.id ?: 0L,
                    name = name,
                    priceUsd = price,
                    billingPeriod = form.billingPeriod,
                    nextBillingEpochDay = form.nextBillingDate.toEpochDay()
                )

                val savedId = if (form.id == null) {
                    repository.insert(entity)
                } else {
                    repository.update(entity)
                    entity.id
                }

                val isPremium = premiumBillingManager.state.value.isPremium
                AppLogger.d("Subscription saved: id=$savedId, premium=$isPremium")

                if (isPremium) {
                    reminderScheduler.schedule(savedId)
                }
            }.onSuccess {
                onSuccess()
            }.onFailure { throwable ->
                AppLogger.e("Failed to save subscription", throwable)
                onError("Could not save the subscription")
            }
        }
    }

    fun deleteSubscription(item: SubscriptionUiModel) {
        viewModelScope.launch {
            runCatching {
                repository.delete(
                    SubscriptionEntity(
                        id = item.id,
                        name = item.name,
                        priceUsd = item.priceUsd,
                        billingPeriod = item.billingPeriod,
                        nextBillingEpochDay = item.nextBillingDate.toEpochDay()
                    )
                )
                reminderScheduler.cancel(item.id)
                AppLogger.d("Subscription deleted: id=${item.id}")
            }.onFailure { throwable ->
                AppLogger.e("Failed to delete subscription", throwable)
            }
        }
    }

    private suspend fun syncReminders(isPremium: Boolean) {
        repository.getAll().forEach { subscription ->
            if (isPremium) {
                reminderScheduler.schedule(subscription.id)
            } else {
                reminderScheduler.cancel(subscription.id)
            }
        }
    }

    private suspend fun normalizeExpiredSubscriptions() {
        val today = LocalDate.now()
        repository.getAll().forEach { subscription ->
            val currentBillingDate = LocalDate.ofEpochDay(subscription.nextBillingEpochDay)
            if (!currentBillingDate.isBefore(today)) return@forEach

            val updatedBillingDate = nextActiveBillingDate(
                currentDate = currentBillingDate,
                billingPeriod = subscription.billingPeriod,
                today = today
            )

            repository.update(
                subscription.copy(nextBillingEpochDay = updatedBillingDate.toEpochDay())
            )
            AppLogger.d(
                "Normalized expired billing date: id=${subscription.id}, from=$currentBillingDate, to=$updatedBillingDate"
            )
        }
    }

    private fun nextActiveBillingDate(
        currentDate: LocalDate,
        billingPeriod: BillingPeriod,
        today: LocalDate
    ): LocalDate {
        var candidate = currentDate
        while (candidate.isBefore(today)) {
            candidate = when (billingPeriod) {
                BillingPeriod.MONTHLY -> candidate.plus(1, ChronoUnit.MONTHS)
                BillingPeriod.YEARLY -> candidate.plus(1, ChronoUnit.YEARS)
            }
        }
        return candidate
    }

    companion object {
        fun factory(
            repository: SubscriptionRepository,
            reminderScheduler: ReminderScheduler,
            premiumBillingManager: PremiumBillingManager
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SubscriptionViewModel(
                        repository = repository,
                        reminderScheduler = reminderScheduler,
                        premiumBillingManager = premiumBillingManager
                    ) as T
                }
            }
        }
    }
}
