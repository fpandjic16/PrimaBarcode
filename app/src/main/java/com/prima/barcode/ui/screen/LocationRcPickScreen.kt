package com.prima.barcode.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prima.barcode.R
import com.prima.barcode.data.model.Location
import com.prima.barcode.data.model.ResponsibilityCenter
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.component.verticalScrollbar
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val lrcSyncFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationRcPickScreen(
    currentRcCode: String,
    currentLocationCode: String,
    availableRcs: List<ResponsibilityCenter>,
    availableLocations: List<Location>,
    isRefreshing: Boolean = false,
    lastSyncedAt: Instant? = null,
    hasCredentials: Boolean = false,
    navDomain: String = "",
    credentialTtlHours: Int = 24,
    onSelect: (rcCode: String, locationCode: String) -> Unit,
    onRefresh: () -> Unit = {},
    onSaveCredentials: (String, String, String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
) {
    var selectedRcCode       by remember { mutableStateOf(currentRcCode) }
    var selectedLocationCode by remember { mutableStateOf(currentLocationCode) }
    var showLoginSheet       by remember { mutableStateOf(false) }
    var showRcSheet          by remember { mutableStateOf(false) }
    var showLocationSheet    by remember { mutableStateOf(false) }

    val selectedRc = remember(selectedRcCode, availableRcs) {
        availableRcs.find { it.code == selectedRcCode }
    }
    val locationsForRc = remember(selectedRcCode, availableLocations) {
        availableLocations.filter { it.rc == selectedRcCode }
    }
    val selectedLocation = remember(selectedLocationCode, locationsForRc) {
        locationsForRc.find { it.code == selectedLocationCode }
    }

    Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
        PrimaTopBar(
            title = stringResource(R.string.lrc_title),
            subtitle = if (lastSyncedAt != null)
                stringResource(R.string.lrc_synced_at, lrcSyncFmt.format(lastSyncedAt))
            else
                stringResource(R.string.lrc_select_subtitle),
            onBack = onBack,
            actions = {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
                    IconButton(onClick = { if (hasCredentials) onRefresh() else showLoginSheet = true }) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh),
                            tint = Color.White,
                        )
                    }
                }
            },
        )

        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LrcSectionHeader(stringResource(R.string.lrc_section_rc))
            Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                LrcSelectionRow(
                    avatar = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedRc != null) PrimaPalette.Slate else PrimaPalette.CreamAlt),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                selectedRc?.let { it.short ?: it.code.take(2) } ?: "—",
                                style = monoLabel.copy(
                                    color = if (selectedRc != null) Color.White else PrimaPalette.Ink3,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    },
                    title = selectedRc?.name ?: stringResource(R.string.lrc_rc_placeholder),
                    subtitle = selectedRc?.code,
                    hasValue = selectedRc != null,
                    onClick = { showRcSheet = true },
                )
            }

            if (selectedRcCode.isNotEmpty()) {
                LrcSectionHeader(stringResource(R.string.lrc_section_location))
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    LrcSelectionRow(
                        avatar = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selectedLocation != null) PrimaPalette.Teal else PrimaPalette.CreamAlt),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = if (selectedLocation != null) Color.White else PrimaPalette.Ink3,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        title = selectedLocation?.name ?: stringResource(R.string.lrc_location_placeholder),
                        subtitle = selectedLocation?.code,
                        hasValue = selectedLocation != null,
                        onClick = { showLocationSheet = true },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = { onSelect(selectedRcCode, selectedLocationCode) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = selectedRcCode.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaPalette.Slate),
            ) {
                Text(stringResource(R.string.btn_apply), fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showRcSheet) {
        RcPickerSheet(
            availableRcs = availableRcs,
            selectedRcCode = selectedRcCode,
            onSelect = { rc ->
                if (rc.code != selectedRcCode) {
                    selectedRcCode = rc.code
                    selectedLocationCode = ""
                }
                showRcSheet = false
            },
            onDismiss = { showRcSheet = false },
        )
    }

    if (showLocationSheet) {
        LocationPickerSheet(
            locations = locationsForRc,
            selectedLocationCode = selectedLocationCode,
            onSelect = { loc ->
                selectedLocationCode = loc.code
                showLocationSheet = false
            },
            onDismiss = { showLocationSheet = false },
        )
    }

    if (showLoginSheet) {
        LoginSheet(
            initialDomain = navDomain,
            credentialTtlHours = credentialTtlHours,
            ctaLabel = stringResource(R.string.btn_sign_in_sync),
            onSubmit = { d, u, p ->
                onSaveCredentials(d, u, p)
                showLoginSheet = false
                onRefresh()
            },
            onDismiss = { showLoginSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RcPickerSheet(
    availableRcs: List<ResponsibilityCenter>,
    selectedRcCode: String,
    onSelect: (ResponsibilityCenter) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, availableRcs) {
        if (query.isBlank()) availableRcs
        else availableRcs.filter {
            it.name.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true)
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PrimaPalette.Cream,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
        ) {
            LrcSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.lrc_search_placeholder),
            )
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.lrc_no_results), style = monoLabel.copy(color = PrimaPalette.Ink3))
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.White)
                        .verticalScrollbar(listState),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    state = listState,
                ) {
                    itemsIndexed(filtered, key = { _, rc -> rc.code }) { index, rc ->
                        RcSelectRow(
                            rc = rc,
                            selected = rc.code == selectedRcCode,
                            onClick = { onSelect(rc) },
                        )
                        if (index < filtered.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = Color(0x0A000000),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationPickerSheet(
    locations: List<Location>,
    selectedLocationCode: String,
    onSelect: (Location) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, locations) {
        if (query.isBlank()) locations
        else locations.filter {
            it.name.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true)
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PrimaPalette.Cream,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
        ) {
            LrcSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.lrc_search_placeholder),
            )
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (locations.isEmpty()) stringResource(R.string.lrc_no_locations)
                        else stringResource(R.string.lrc_no_results),
                        style = monoLabel.copy(color = PrimaPalette.Ink3),
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.White)
                        .verticalScrollbar(listState),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    state = listState,
                ) {
                    itemsIndexed(filtered, key = { _, loc -> loc.code }) { index, loc ->
                        LocationSelectRow(
                            location = loc,
                            selected = loc.code == selectedLocationCode,
                            onClick = { onSelect(loc) },
                        )
                        if (index < filtered.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = Color(0x0A000000),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LrcSheetHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = PrimaPalette.Ink,
            ),
        )
    }
    HorizontalDivider(color = Color(0x0F000000))
}

@Composable
private fun LrcSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrimaPalette.Cream)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, style = monoLabel.copy(color = PrimaPalette.Ink3)) },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = PrimaPalette.Ink3,
                    modifier = Modifier.size(18.dp),
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = PrimaPalette.Slate,
                unfocusedBorderColor = Color(0x18000000),
            ),
            textStyle = monoLabel.copy(color = PrimaPalette.Ink),
        )
    }
}

@Composable
private fun LrcSelectionRow(
    avatar: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    hasValue: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = if (hasValue) PrimaPalette.Ink else PrimaPalette.Ink3,
                ),
            )
            if (subtitle != null) {
                Text(subtitle, style = monoLabel.copy(color = PrimaPalette.Ink3))
            }
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = PrimaPalette.Ink3,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun LrcSectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        style = monoLabel.copy(color = PrimaPalette.Ink3),
    )
}

@Composable
private fun RcSelectRow(rc: ResponsibilityCenter, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) PrimaPalette.Slate else PrimaPalette.CreamAlt),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                rc.short ?: rc.code.take(2),
                style = monoLabel.copy(
                    color = if (selected) Color.White else PrimaPalette.Ink2,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                rc.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = PrimaPalette.Ink,
                ),
            )
            Text(rc.code, style = monoLabel.copy(color = PrimaPalette.Ink3))
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = PrimaPalette.Slate,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LocationSelectRow(location: Location, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) PrimaPalette.Teal else PrimaPalette.CreamAlt),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = if (selected) Color.White else PrimaPalette.Ink3,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                location.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = PrimaPalette.Ink,
                ),
            )
            Text(location.code, style = monoLabel.copy(color = PrimaPalette.Ink3))
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = PrimaPalette.Teal,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
