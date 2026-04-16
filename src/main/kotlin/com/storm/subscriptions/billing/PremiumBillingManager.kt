package io.github.folk97stormi.subtrack.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import io.github.folk97stormi.subtrack.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PremiumBillingState(
    val isPremium: Boolean = false,
    val isLoading: Boolean = true,
    val isBillingAvailable: Boolean = false,
    val canPurchase: Boolean = false,
    val hasPendingPurchase: Boolean = false,
    val priceLabel: String? = null
)

class PremiumBillingManager(context: Context) : PurchasesUpdatedListener {
    private val preferences = PremiumPreferences(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    private var productDetails: ProductDetails? = null
    private var restoreRequested = false

    private val _state = MutableStateFlow(
        PremiumBillingState(
            isPremium = preferences.isPremium(),
            isLoading = true
        )
    )
    val state: StateFlow<PremiumBillingState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        connect()
    }

    fun launchPurchase(activity: Activity): String? {
        val details = productDetails
        if (!_state.value.isBillingAvailable || details == null) {
            connect()
            return "Premium is not available yet. Try again in a few seconds."
        }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()

        val result = billingClient.launchBillingFlow(activity, params)
        return when (result.responseCode) {
            BillingResponseCode.OK -> null
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                restoreRequested = true
                refreshPurchases(emitRestoreMessage = true)
                "Premium is already owned on this account. Checking your purchase."
            }
            else -> responseMessage(result.responseCode)
        }
    }

    fun restorePurchases(): String? {
        restoreRequested = true
        return if (billingClient.isReady) {
            refreshPurchases(emitRestoreMessage = true)
            null
        } else {
            connect()
            "Connecting to Google Play and checking your purchases."
        }
    }

    fun close() {
        billingClient.endConnection()
    }

    override fun onPurchasesUpdated(
        billingResult: com.android.billingclient.api.BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> handlePurchases(purchases.orEmpty(), emitRestoreMessage = false)
            BillingResponseCode.USER_CANCELED -> emitEvent("Purchase canceled.")
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                restoreRequested = true
                refreshPurchases(emitRestoreMessage = true)
            }
            else -> emitEvent(responseMessage(billingResult.responseCode))
        }
    }

    private fun connect() {
        if (billingClient.isReady) {
            refreshCatalog()
            refreshPurchases(emitRestoreMessage = false)
            return
        }

        _state.update { it.copy(isLoading = true) }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: com.android.billingclient.api.BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    _state.update { it.copy(isLoading = true, isBillingAvailable = true) }
                    refreshCatalog()
                    refreshPurchases(emitRestoreMessage = false)
                } else {
                    AppLogger.e("Billing setup failed: ${result.responseCode}")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isBillingAvailable = false,
                            canPurchase = false
                        )
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isBillingAvailable = false,
                        canPurchase = false
                    )
                }
            }
        })
    }

    private fun refreshCatalog() {
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                )
                .build()
        ) { result, products ->
            if (result.responseCode != BillingResponseCode.OK) {
                AppLogger.e("Product details query failed: ${result.responseCode}")
                _state.update {
                    it.copy(
                        isLoading = false,
                        canPurchase = false,
                        priceLabel = null
                    )
                }
                return@queryProductDetailsAsync
            }

            productDetails = products.firstOrNull()
            _state.update {
                it.copy(
                    isLoading = false,
                    canPurchase = productDetails != null,
                    priceLabel = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
                )
            }
        }
    }

    private fun refreshPurchases(emitRestoreMessage: Boolean) {
        if (!billingClient.isReady) {
            connect()
            return
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode != BillingResponseCode.OK) {
                AppLogger.e("Purchases query failed: ${result.responseCode}")
                _state.update { it.copy(isLoading = false) }
                if (emitRestoreMessage) {
                    emitEvent("Could not verify Premium purchases. Try again.")
                    restoreRequested = false
                }
                return@queryPurchasesAsync
            }

            handlePurchases(purchases, emitRestoreMessage)
        }
    }

    private fun handlePurchases(purchases: List<Purchase>, emitRestoreMessage: Boolean) {
        val activePurchase = purchases.firstOrNull {
            it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        val pendingPurchase = purchases.any {
            it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PENDING
        }
        val isPremium = activePurchase != null

        preferences.setPremium(isPremium)
        _state.update {
            it.copy(
                isPremium = isPremium,
                isLoading = false,
                hasPendingPurchase = pendingPurchase
            )
        }

        if (activePurchase != null && !activePurchase.isAcknowledged) {
            acknowledge(activePurchase)
        }

        if (emitRestoreMessage || restoreRequested) {
            emitEvent(
                when {
                    isPremium -> "Premium unlocked."
                    pendingPurchase -> "Your Premium purchase is still processing in Google Play."
                    else -> "No Premium purchases were found for this account."
                }
            )
            restoreRequested = false
        } else if (pendingPurchase) {
            emitEvent("Your Premium purchase is still processing in Google Play.")
        }
    }

    private fun acknowledge(purchase: Purchase) {
        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        ) { result ->
            if (result.responseCode == BillingResponseCode.OK) {
                emitEvent("Premium unlocked.")
            } else {
                AppLogger.e("Acknowledge purchase failed: ${result.responseCode}")
            }
        }
    }

    private fun emitEvent(message: String) {
        scope.launch {
            _events.emit(message)
        }
    }

    private fun responseMessage(code: Int): String {
        return when (code) {
            BillingResponseCode.BILLING_UNAVAILABLE -> "Google Play Billing is unavailable on this device."
            BillingResponseCode.NETWORK_ERROR -> "Network unavailable. Check your connection and try again."
            BillingResponseCode.SERVICE_UNAVAILABLE -> "Google Play is temporarily unavailable."
            BillingResponseCode.DEVELOPER_ERROR -> "Premium has not been configured in Google Play Console yet."
            BillingResponseCode.FEATURE_NOT_SUPPORTED -> "Purchases are not supported on this device."
            BillingResponseCode.ITEM_UNAVAILABLE -> "Premium is currently unavailable in Google Play."
            BillingResponseCode.ERROR -> "Google Play returned an error while processing the purchase."
            else -> "Could not complete the Premium action."
        }
    }

    companion object {
        const val PRODUCT_ID = "premium_unlock"
    }
}
