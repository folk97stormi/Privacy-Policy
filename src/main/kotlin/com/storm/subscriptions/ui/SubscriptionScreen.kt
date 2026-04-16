package io.github.folk97stormi.subtrack.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.folk97stormi.subtrack.R
import io.github.folk97stormi.subtrack.data.BillingPeriod
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val suggestions = listOf(
    "Netflix",
    "Spotify Premium",
    "YouTube Premium",
    "Disney+",
    "Hulu",
    "Google One"
)

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val moneyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)

@DrawableRes
private fun serviceIcon(name: String): Int {
    return when {
        name.contains("Netflix", true) -> R.drawable.ic_service_netflix
        name.contains("Spotify Premium", true) -> R.drawable.ic_service_spotify
        name.contains("YouTube", true) -> R.drawable.ic_service_youtube
        name.contains("Google", true) -> R.drawable.ic_service_googleone
        else -> R.drawable.ic_service_generic
    }
}

@Composable
fun SubscriptionApp(
    viewModel: SubscriptionViewModel,
    onUpgradeClick: () -> String?,
    onRestoreClick: () -> String?,
    onPrivacyPolicyClick: () -> Unit
) {
    SubscriptionTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            SubscriptionScreen(
                viewModel = viewModel,
                onUpgradeClick = onUpgradeClick,
                onRestoreClick = onRestoreClick,
                onPrivacyPolicyClick = onPrivacyPolicyClick
            )
        }
    }
}

@Composable
private fun SubscriptionScreen(
    viewModel: SubscriptionViewModel,
    onUpgradeClick: () -> String?,
    onRestoreClick: () -> String?,
    onPrivacyPolicyClick: () -> Unit
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var formState by remember { mutableStateOf<SubscriptionFormState?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.billingMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    formState = SubscriptionFormState()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 10.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add subscription")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(screenBackground())
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = 100.dp
                )
            ) {
                item {
                    HeroHeader(
                        monthlyTotal = state.monthlyTotal,
                        yearlyTotal = state.yearlyTotal,
                        subscriptionsCount = state.items.size,
                        isPremium = state.isPremium,
                        isBillingLoading = state.isBillingLoading,
                        isBillingAvailable = state.isBillingAvailable,
                        canPurchasePremium = state.canPurchasePremium,
                        hasPendingPurchase = state.hasPendingPurchase,
                        premiumPriceLabel = state.premiumPriceLabel,
                        onUpgradeClick = {
                            onUpgradeClick()?.let { message ->
                                scope.launch { snackbarHostState.showSnackbar(message) }
                            }
                        },
                        onRestoreClick = {
                            onRestoreClick()?.let { message ->
                                scope.launch { snackbarHostState.showSnackbar(message) }
                            }
                        }
                    )
                }

                if (state.items.isEmpty()) {
                    item {
                        EmptyState(onAdd = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            formState = SubscriptionFormState()
                        })
                    }
                } else {
                    item {
                        Text(
                            text = "Active subscriptions",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                        SubscriptionCard(
                            item = item,
                            index = index,
                            onEdit = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                formState = SubscriptionFormState(
                                    id = item.id,
                                    name = item.name,
                                    price = item.priceUsd.toString(),
                                    billingPeriod = item.billingPeriod,
                                    nextBillingDate = item.nextBillingDate
                                )
                            },
                            onDelete = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.deleteSubscription(item)
                            }
                        )
                    }
                }
                if (!state.isPremium) {
                    item {
                        PremiumActionsCard(
                            isBillingLoading = state.isBillingLoading,
                            isBillingAvailable = state.isBillingAvailable,
                            canPurchasePremium = state.canPurchasePremium,
                            hasPendingPurchase = state.hasPendingPurchase,
                            premiumPriceLabel = state.premiumPriceLabel,
                            onUpgradeClick = {
                                onUpgradeClick()?.let { message ->
                                    scope.launch { snackbarHostState.showSnackbar(message) }
                                }
                            },
                            onRestoreClick = {
                                onRestoreClick()?.let { message ->
                                    scope.launch { snackbarHostState.showSnackbar(message) }
                                }
                            }
                        )
                    }
                }
                item {
                    TextButton(onClick = onPrivacyPolicyClick) {
                        Text("Privacy policy")
                    }
                }
            }
        }
    }

    formState?.let { form ->
        SubscriptionBottomSheet(
            initialForm = form,
            canAddMore = state.canAddMore || form.id != null,
            onDismiss = { formState = null },
            onSave = { updated ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.saveSubscription(
                    form = updated,
                    onSuccess = { formState = null },
                    onError = { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    }
                )
            }
        )
    }

}

@Composable
private fun HeroHeader(
    monthlyTotal: Double,
    yearlyTotal: Double,
    subscriptionsCount: Int,
    isPremium: Boolean,
    isBillingLoading: Boolean,
    isBillingAvailable: Boolean,
    canPurchasePremium: Boolean,
    hasPendingPurchase: Boolean,
    premiumPriceLabel: String?,
    onUpgradeClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.surfaceVariant.copy(alpha = 0.95f),
                        colors.surface.copy(alpha = 0.88f),
                        colors.secondary.copy(alpha = 0.18f)
                    )
                )
            )
            .border(1.dp, colors.outline.copy(alpha = 0.35f), RoundedCornerShape(32.dp))
            .padding(24.dp)
    ) {
        AmbientGlow(modifier = Modifier.matchParentSize())
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Your subscriptions",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onBackground
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Monthly spend",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = moneyFormatter.format(monthlyTotal),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.onBackground
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassPill(text = "$subscriptionsCount active", accent = colors.primary)
                GlassPill(
                    text = if (isPremium) "Premium" else "Free",
                    accent = if (isPremium) colors.tertiary else colors.secondary
                )
                if (hasPendingPurchase) {
                    GlassPill(text = "Pending", accent = colors.primary)
                }
            }
            HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryMetric(label = "Annual spend", value = moneyFormatter.format(yearlyTotal))
                SummaryMetric(
                    label = "Average subscription",
                    value = moneyFormatter.format(if (subscriptionsCount == 0) 0 else monthlyTotal / subscriptionsCount)
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun GlassPill(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun ServicePill(
    text: String,
    accent: Color,
    @DrawableRes iconRes: Int
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = text,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun AmbientGlow(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp, start = 180.dp)
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                .graphicsLayer { alpha = 0.9f }
        )
        Box(
            modifier = Modifier
                .padding(top = 120.dp)
                .size(90.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(colors.surface.copy(alpha = 0.78f))
            .border(1.dp, colors.outline.copy(alpha = 0.24f), RoundedCornerShape(32.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EmptyIllustration()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Add your first subscription",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Track your streaming, cloud, and subscription bills in one place and see your monthly spend instantly.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant
            )
        }
        GlassActionButton(text = "Get started", onClick = onAdd)
    }
}

@Composable
private fun EmptyIllustration() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(170.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.primary.copy(alpha = 0.28f),
                        colors.secondary.copy(alpha = 0.14f),
                        Color.Transparent
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(colors.surface.copy(alpha = 0.72f))
                .border(1.dp, colors.outline.copy(alpha = 0.22f), RoundedCornerShape(36.dp))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(18.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.16f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(colors.tertiary.copy(alpha = 0.18f))
            )
            Icon(
                imageVector = Icons.Rounded.Payments,
                contentDescription = null,
                tint = colors.onBackground,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(46.dp)
            )
        }
    }
}

@Composable
private fun GlassActionButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun PremiumActionsCard(
    isBillingLoading: Boolean,
    isBillingAvailable: Boolean,
    canPurchasePremium: Boolean,
    hasPendingPurchase: Boolean,
    premiumPriceLabel: String?,
    onUpgradeClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surface.copy(alpha = 0.76f))
            .border(1.dp, colors.outline.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Premium",
            style = MaterialTheme.typography.titleLarge,
            color = colors.onBackground
        )
        Text(
            text = when {
                hasPendingPurchase -> "Google Play is still processing your purchase. Premium will unlock automatically once confirmed."
                isBillingLoading -> "Connecting to Google Play..."
                canPurchasePremium && premiumPriceLabel != null -> "Unlock unlimited subscriptions and renewal reminders for $premiumPriceLabel."
                isBillingAvailable -> "Unlock unlimited subscriptions and renewal reminders."
                else -> "Google Play Billing is unavailable right now. Check Google Play and try again."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassActionButton(
                text = when {
                    isBillingLoading -> "Please wait..."
                    premiumPriceLabel != null -> "Get Premium for $premiumPriceLabel"
                    else -> "Get Premium"
                },
                onClick = {
                    if (!isBillingLoading && canPurchasePremium) {
                        onUpgradeClick()
                    }
                }
            )
            TextButton(onClick = onRestoreClick) {
                Text("Restore")
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    item: SubscriptionUiModel,
    index: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var visible by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        kotlinx.coroutines.delay((index * 55L).coerceAtMost(240L))
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(450)) + slideInVertically(
            animationSpec = tween(450, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 3 }
        ),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 5 })
    ) {
        val daysLeft = daysUntil(item.nextBillingDate)
        val progress = billingProgress(item.billingPeriod, item.nextBillingDate)
        val serviceTone = serviceTone(item.name)
        val colors = MaterialTheme.colorScheme

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surface.copy(alpha = 0.76f))
                .border(1.dp, colors.outline.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(serviceTone.copy(alpha = 0.18f))
                            .border(1.dp, serviceTone.copy(alpha = 0.18f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = serviceIcon(item.name)),
                            contentDescription = item.name,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = moneyFormatter.format(item.priceUsd),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.primary
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit subscription")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete subscription")
                    }
                }
            }

            Text(
                text = "Next charge ${item.nextBillingDate.format(dateFormatter)}",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (daysLeft == 0L) "Today" else "In ${daysText(daysLeft)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onBackground
                    )
                    Text(
                        text = if (item.billingPeriod == BillingPeriod.MONTHLY) "Monthly" else "Yearly",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = serviceTone,
                    trackColor = colors.surfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SubscriptionBottomSheet(
    initialForm: SubscriptionFormState,
    canAddMore: Boolean,
    onDismiss: () -> Unit,
    onSave: (SubscriptionFormState) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentForm by remember(initialForm) { mutableStateOf(initialForm) }
    var showNameMenu by remember { mutableStateOf(false) }
    var showPeriodMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(width = 42.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = if (initialForm.id == null) "New subscription" else "Edit subscription",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (canAddMore) {
                    "Add a service and instantly see your updated subscription spending."
                } else {
                    "You have reached the free plan limit. You can still edit your current subscriptions."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ExposedDropdownMenuBox(
                expanded = showNameMenu,
                onExpandedChange = { showNameMenu = !showNameMenu }
            ) {
                StyledField(
                    value = currentForm.name,
                    onValueChange = {
                        currentForm = currentForm.copy(name = it)
                        showNameMenu = true
                    },
                    label = "Service name",
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    trailing = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) }
                )
                ExposedDropdownMenu(
                    expanded = showNameMenu,
                    onDismissRequest = { showNameMenu = false }
                ) {
                    suggestions
                        .filter {
                            currentForm.name.isBlank() || it.contains(currentForm.name, ignoreCase = true)
                        }
                        .forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = {
                                    currentForm = currentForm.copy(name = suggestion)
                                    showNameMenu = false
                                }
                            )
                        }
                }
            }

            StyledField(
                value = currentForm.price,
                onValueChange = {
                    currentForm = currentForm.copy(
                        price = it.filter { symbol -> symbol.isDigit() || symbol == ',' || symbol == '.' }
                    )
                },
                label = "Price, USD"
            )

            ExposedDropdownMenuBox(
                expanded = showPeriodMenu,
                onExpandedChange = { showPeriodMenu = !showPeriodMenu }
            ) {
                StyledField(
                    value = if (currentForm.billingPeriod == BillingPeriod.MONTHLY) "Monthly" else "Yearly",
                    onValueChange = {},
                    readOnly = true,
                    label = "Billing cycle",
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    trailing = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) }
                )
                ExposedDropdownMenu(
                    expanded = showPeriodMenu,
                    onDismissRequest = { showPeriodMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Monthly") },
                        onClick = {
                            currentForm = currentForm.copy(billingPeriod = BillingPeriod.MONTHLY)
                            showPeriodMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Yearly") },
                        onClick = {
                            currentForm = currentForm.copy(billingPeriod = BillingPeriod.YEARLY)
                            showPeriodMenu = false
                        }
                    )
                }
            }

            GlassPanel {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Next billing date",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentForm.nextBillingDate.format(dateFormatter),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                suggestions.take(4).forEach { title ->
                    ServicePill(
                        text = title,
                        accent = serviceTone(title),
                        iconRes = serviceIcon(title)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Text("Cancel")
                }
                Box(modifier = Modifier.weight(1f)) {
                    GlassActionButton(
                        text = if (initialForm.id == null) "Save" else "Update",
                        onClick = { onSave(currentForm) }
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        SubscriptionDatePicker(
            initialDate = currentForm.nextBillingDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { selected ->
                currentForm = currentForm.copy(nextBillingDate = selected)
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun GlassPanel(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        label = { Text(label) },
        trailingIcon = trailing,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionDatePicker(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate.toEpochMillis())

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = pickerState.selectedDateMillis?.let { millis ->
                        LocalDate.ofEpochDay(millis / MILLIS_PER_DAY)
                    } ?: initialDate
                    onConfirm(selected)
                }
            ) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun screenBackground(): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.background
        )
    )
}

private fun LocalDate.toEpochMillis(): Long {
    return atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun billingProgress(period: BillingPeriod, billingDate: LocalDate): Float {
    val total = if (period == BillingPeriod.MONTHLY) 30f else 365f
    val remaining = daysUntil(billingDate).coerceAtLeast(0).toFloat().coerceAtMost(total)
    return (1f - remaining / total).coerceIn(0.05f, 1f)
}

private fun daysUntil(date: LocalDate): Long {
    return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date).coerceAtLeast(0)
}

private fun daysText(days: Long): String {
    return "$days day" + if (days == 1L) "" else "s"
}

private fun serviceTone(name: String): Color {
    return when {
        name.contains("Netflix", true) -> Color(0xFFFF5C70)
        name.contains("Spotify", true) -> Color(0xFF4EE28A)
        name.contains("YouTube", true) -> Color(0xFFFF6A5C)
        name.contains("Disney", true) -> Color(0xFF7E8BFF)
        name.contains("Google", true) -> Color(0xFF56A3FF)
        name.contains("Hulu", true) -> Color(0xFF5DA8FF)
        else -> Color(0xFF7CFFB2)
    }
}

private const val MILLIS_PER_DAY = 86_400_000L
