package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.saveRealRoute
import com.vincenthzr.locationspoofer.viewmodel.useRealRoute
import com.vincenthzr.locationspoofer.data.model.RoutePlanStage
import kotlinx.coroutines.delay

@Composable
internal fun RealRouteRecordingDialog(viewModel: MainViewModel, spoofing: Boolean, onDismiss: () -> Unit) {
    val recorder = viewModel.realRouteRecorder
    val appState by viewModel.uiState.collectAsState()
    var statsNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(recorder.recording) {
        statsNow = SystemClock.elapsedRealtime()
        while (recorder.recording) {
            delay(1000)
            statsNow = SystemClock.elapsedRealtime()
        }
    }
    val recordedDistance = remember(recorder.points) { realRouteDistanceMeters(recorder.points) }
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager)
            .getNotificationChannel("real_route_recording")
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }
    var notificationAllowed by remember { mutableStateOf(notificationsAllowed()) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) notificationAllowed = notificationsAllowed() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var name by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var confirmUnreadable by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmResume by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        permissionDenied = !granted
        if (granted && !spoofing && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) recorder.start()
    }
    LaunchedEffect(spoofing) { if (spoofing) recorder.pause() }
    LaunchedEffect(clearing, recorder.draftPending, recorder.storageError) {
        if (clearing && !recorder.draftPending && !recorder.storageError) { clearing = false; onDismiss() }
    }
    val dismiss = {
        if (recorder.points.isNotEmpty()) confirmDiscard = true else onDismiss()
    }
    val canSave = !saving && !clearing && recorder.points.size >= 2 && name.isNotBlank()
    val canUse = !spoofing && !appState.isFetchingRoute && appState.routePlanStage != RoutePlanStage.RUNNING
    val save: (Boolean) -> Unit = { useAfterSave ->
        recorder.pause()
        val points = recorder.points
        val routeName = name.trim()
        saving = true
        saveFailed = false
        viewModel.saveRealRoute(routeName, points) { success ->
            saving = false
            if (success) {
                if (useAfterSave && !viewModel.useRealRoute(points)) {
                    android.widget.Toast.makeText(context, context.getString(R.string.real_route_saved_but_not_loaded), android.widget.Toast.LENGTH_LONG).show()
                }
                recorder.clear()
                clearing = true
            } else saveFailed = true
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving && !clearing) dismiss() },
        title = { Text(stringResource(R.string.real_route_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.real_route_foreground_hint))
                if (!notificationAllowed) Text(stringResource(R.string.real_route_notification_denied), color = MaterialTheme.colorScheme.error)
                if (spoofing) Text(stringResource(R.string.real_route_stop_spoof), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.real_route_stats, recordedDistance.toInt(), formatRecordingDuration(recorder.statistics.elapsed(statsNow)), recorder.points.size))
                Text(stringResource(R.string.real_route_stats_rules), style = MaterialTheme.typography.bodySmall)
                if (permissionDenied) Text(stringResource(R.string.real_route_permission_error), color = MaterialTheme.colorScheme.error)
                if (recorder.error != null && !(recorder.error == "gap" && recorder.hasGap) &&
                    !(recorder.error == "storage" && recorder.storageError) &&
                    !(recorder.error == "spoofing" && spoofing) &&
                    !(recorder.error == "permission" && permissionDenied)) Text(stringResource(when (recorder.error) {
                    "gps" -> R.string.real_route_gps_error
                    "limit" -> R.string.real_route_limit
                    "service" -> R.string.real_route_service_error
                    "spoofing" -> R.string.real_route_stop_spoof
                    "storage" -> R.string.real_route_draft_error
                    "gap" -> R.string.real_route_gap
                    else -> R.string.real_route_permission_error
                }), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (recorder.hasGap) {
                    Text(stringResource(R.string.real_route_gap), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick = recorder::acknowledgeGap) { Text(stringResource(R.string.real_route_gap_acknowledge)) }
                }
                if (recorder.storageError) {
                    Text(stringResource(R.string.real_route_draft_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    if (clearing) TextButton(onClick = recorder::retryDiscardDraft) { Text(stringResource(R.string.real_route_draft_retry)) }
                    else if (recorder.unreadableDraft) TextButton(onClick = { confirmUnreadable = true }) { Text(stringResource(R.string.real_route_draft_discard_corrupt)) }
                    else if (recorder.points.isNotEmpty()) TextButton(onClick = recorder::retryDraftSave) { Text(stringResource(R.string.real_route_draft_retry)) }
                }
                if (saveFailed) Text(stringResource(R.string.real_route_save_failed), color = MaterialTheme.colorScheme.error)
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.real_route_name)) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (recorder.recording) {
                        OutlinedButton(onClick = recorder::pause) { Text(stringResource(R.string.real_route_pause)) }
                    } else {
                        OutlinedButton(enabled = !spoofing && !saving && !clearing && !recorder.starting && !recorder.storageError && !recorder.draftPending && !recorder.atLimit, onClick = {
                            if (recorder.points.isEmpty()) permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            else confirmResume = true
                        }) {
                            Text(stringResource(if (recorder.points.isEmpty()) R.string.real_route_start else R.string.real_route_resume))
                        }
                    }
                }
                OutlinedButton(enabled = canSave && canUse, onClick = { save(true) }) {
                    Text(stringResource(R.string.real_route_save_use))
                }
                if (!canUse) Text(stringResource(R.string.real_route_use_unavailable), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = { save(false) }) { Text(stringResource(R.string.real_route_save)) }
        },
        dismissButton = { TextButton(enabled = !saving && !clearing, onClick = dismiss) { Text(stringResource(R.string.real_route_close)) } }
    )
    if (confirmUnreadable) AlertDialog(
        onDismissRequest = { confirmUnreadable = false },
        title = { Text(stringResource(R.string.real_route_draft_discard_corrupt)) },
        text = { Text(stringResource(R.string.real_route_draft_corrupt_confirm)) },
        confirmButton = { TextButton(onClick = { recorder.clear(); clearing = true; confirmUnreadable = false }) { Text(stringResource(R.string.real_route_discard)) } },
        dismissButton = { TextButton(onClick = { confirmUnreadable = false }) { Text(stringResource(R.string.real_route_keep)) } }
    )
    if (confirmResume) AlertDialog(
        onDismissRequest = { confirmResume = false },
        title = { Text(stringResource(R.string.real_route_resume_title)) },
        text = { Text(stringResource(R.string.real_route_resume_warning)) },
        confirmButton = {
            TextButton(onClick = {
                confirmResume = false
                permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text(stringResource(R.string.real_route_resume)) }
        },
        dismissButton = { TextButton(onClick = { confirmResume = false }) { Text(stringResource(R.string.real_route_keep)) } }
    )
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.real_route_discard_title)) },
        text = { Text(stringResource(R.string.real_route_discard_message)) },
        confirmButton = { TextButton(onClick = { recorder.clear(); clearing = true; confirmDiscard = false }) { Text(stringResource(R.string.real_route_discard)) } },
        dismissButton = { TextButton(enabled = !recorder.draftPending && !recorder.storageError, onClick = { confirmDiscard = false; onDismiss() }) { Text(stringResource(R.string.real_route_keep)) } }
    )
}
