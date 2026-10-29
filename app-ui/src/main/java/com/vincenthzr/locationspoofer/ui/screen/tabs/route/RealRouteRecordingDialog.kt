package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.saveRealRoute

@Composable
internal fun RealRouteRecordingDialog(viewModel: MainViewModel, spoofing: Boolean, onDismiss: () -> Unit) {
    val recorder = viewModel.realRouteRecorder
    val owner = LocalLifecycleOwner.current
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
    DisposableEffect(owner, recorder) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) recorder.pause()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); recorder.pause() }
    }
    LaunchedEffect(spoofing) { if (spoofing) recorder.pause() }
    LaunchedEffect(clearing, recorder.draftPending, recorder.storageError) {
        if (clearing && !recorder.draftPending && !recorder.storageError) { clearing = false; onDismiss() }
    }
    val dismiss = {
        if (recorder.points.isNotEmpty()) confirmDiscard = true else { recorder.pause(); onDismiss() }
    }
    AlertDialog(
        onDismissRequest = { if (!saving && !clearing) dismiss() },
        title = { Text(stringResource(R.string.real_route_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.real_route_foreground_hint))
                if (spoofing) Text(stringResource(R.string.real_route_stop_spoof), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.real_route_points, recorder.points.size))
                if (permissionDenied) Text(stringResource(R.string.real_route_permission_error), color = MaterialTheme.colorScheme.error)
                if (recorder.error != null) Text(stringResource(when (recorder.error) {
                    "gps" -> R.string.real_route_gps_error
                    "limit" -> R.string.real_route_limit
                    else -> R.string.real_route_permission_error
                }), color = MaterialTheme.colorScheme.error)
                if (recorder.hasGap) {
                    Text(stringResource(R.string.real_route_gap), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = recorder::acknowledgeGap) { Text(stringResource(R.string.real_route_gap_acknowledge)) }
                }
                if (recorder.storageError) {
                    Text(stringResource(R.string.real_route_draft_error), color = MaterialTheme.colorScheme.error)
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
                        OutlinedButton(enabled = !spoofing && !saving && !clearing && !recorder.unreadableDraft && !recorder.atLimit, onClick = {
                            if (recorder.points.isEmpty()) permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            else confirmResume = true
                        }) {
                            Text(stringResource(if (recorder.points.isEmpty()) R.string.real_route_start else R.string.real_route_resume))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && !clearing && recorder.points.size >= 2 && name.isNotBlank(), onClick = {
                recorder.pause(); saving = true; saveFailed = false
                viewModel.saveRealRoute(name.trim(), recorder.points) { success ->
                    saving = false
                    if (success) { recorder.clear(); clearing = true } else saveFailed = true
                }
            }) { Text(stringResource(R.string.real_route_save)) }
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
        dismissButton = { TextButton(enabled = !recorder.draftPending && !recorder.storageError, onClick = { recorder.pause(); confirmDiscard = false; onDismiss() }) { Text(stringResource(R.string.real_route_keep)) } }
    )
}
