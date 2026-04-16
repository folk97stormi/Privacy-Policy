package io.github.folk97stormi.subtrack

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.folk97stormi.subtrack.billing.PremiumBillingManager
import io.github.folk97stormi.subtrack.billing.PremiumPreferences
import io.github.folk97stormi.subtrack.data.AppDatabase
import io.github.folk97stormi.subtrack.data.BillingPeriod
import io.github.folk97stormi.subtrack.data.SubscriptionEntity
import io.github.folk97stormi.subtrack.data.SubscriptionRepository
import io.github.folk97stormi.subtrack.reminder.ReminderScheduler
import io.github.folk97stormi.subtrack.ui.SubscriptionApp
import io.github.folk97stormi.subtrack.ui.SubscriptionViewModel
import io.github.folk97stormi.subtrack.util.AppLogger
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private val privacyPolicyUri = Uri.parse(PRIVACY_POLICY_URL)
    private val premiumBillingManager by lazy { PremiumBillingManager(applicationContext) }

    private val viewModel by viewModels<SubscriptionViewModel> {
        SubscriptionViewModel.factory(
            repository = SubscriptionRepository(AppDatabase.create(applicationContext).subscriptionDao()),
            reminderScheduler = ReminderScheduler(applicationContext),
            premiumBillingManager = premiumBillingManager
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        seedDebugDemoData()

        setContent {
            SubscriptionApp(
                viewModel = viewModel,
                onUpgradeClick = { premiumBillingManager.launchPurchase(this) },
                onRestoreClick = { premiumBillingManager.restorePurchases() },
                onPrivacyPolicyClick = {
                    startActivity(Intent(Intent.ACTION_VIEW, privacyPolicyUri))
                }
            )
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            premiumBillingManager.close()
        }
        super.onDestroy()
    }

    private fun seedDebugDemoData() {
        if (!BuildConfig.DEBUG) return

        Thread {
            runBlocking {
                runCatching {
                    val repository = SubscriptionRepository(AppDatabase.create(applicationContext).subscriptionDao())
                    if (repository.getAll().isNotEmpty()) {
                        AppLogger.d("Debug demo data already exists")
                        return@runBlocking
                    }

                    PremiumPreferences(applicationContext).setPremium(true)

                    val today = LocalDate.now()
                    val demoItems = listOf(
                        SubscriptionEntity(
                            name = "YouTube Premium",
                            priceUsd = 13.99,
                            billingPeriod = BillingPeriod.MONTHLY,
                            nextBillingEpochDay = today.plusDays(3).toEpochDay()
                        ),
                        SubscriptionEntity(
                            name = "Spotify Premium",
                            priceUsd = 11.99,
                            billingPeriod = BillingPeriod.MONTHLY,
                            nextBillingEpochDay = today.plusDays(8).toEpochDay()
                        ),
                        SubscriptionEntity(
                            name = "Netflix",
                            priceUsd = 15.49,
                            billingPeriod = BillingPeriod.MONTHLY,
                            nextBillingEpochDay = today.plusDays(14).toEpochDay()
                        ),
                        SubscriptionEntity(
                            name = "Google One",
                            priceUsd = 99.99,
                            billingPeriod = BillingPeriod.YEARLY,
                            nextBillingEpochDay = today.plusDays(27).toEpochDay()
                        )
                    )

                    demoItems.forEach { repository.insert(it) }
                    AppLogger.d("Debug demo data seeded: ${demoItems.size}")
                }.onFailure { throwable ->
                    AppLogger.e("Failed to seed debug demo data", throwable)
                }
            }
        }.start()
    }

    companion object {
        private const val PRIVACY_POLICY_URL = "https://paste.rs/I4wfk"
    }
}
