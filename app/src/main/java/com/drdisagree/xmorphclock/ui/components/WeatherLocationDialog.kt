package com.drdisagree.xmorphclock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.domain.model.WeatherCity

@Composable
fun WeatherLocationDialog(
    visible: Boolean,
    currentLocationName: String,
    hasCustomLocation: Boolean,
    results: List<WeatherCity>,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchImmediate: (String) -> Unit,
    onSelectCity: (WeatherCity) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val focus = LocalFocusManager.current
    var query by rememberSaveable(visible) {
        mutableStateOf(if (hasCustomLocation) currentLocationName else "")
    }

    LaunchedEffect(visible) {
        if (query.trim().length >= 2) {
            onQueryChange(query.trim())
        } else {
            onQueryChange("")
        }
    }

    ExpressiveDialogFrame(visible = visible, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.weather_location_choose),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                ExpressiveIconButton(
                    icon = R.drawable.ic_cross,
                    onClick = onDismiss,
                    size = 40.dp,
                    description = stringResource(R.string.action_close),
                    modifier = Modifier.testTag("weather_dialog_close")
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { next ->
                    query = next
                    onQueryChange(next)
                },
                placeholder = {
                    Text(
                        stringResource(R.string.weather_city),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    AppIcon(
                        icon = R.drawable.ic_search,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 20.dp
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        ExpressiveIconButton(
                            icon = R.drawable.ic_cross,
                            onClick = {
                                query = ""
                                onQueryChange("")
                            },
                            size = 36.dp,
                            description = stringResource(R.string.action_clear),
                            modifier = Modifier.testTag("weather_city_clear_button")
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focus.clearFocus()
                        onSearchImmediate(query)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weather_city_search_input")
            )

            val autoContainer = if (!hasCustomLocation) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
            val autoContent = if (!hasCustomLocation) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            Surface(
                onClick = {
                    onUseCurrentLocation()
                    onDismiss()
                },
                shape = RoundedCornerShape(18.dp),
                color = autoContainer,
                contentColor = autoContent,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { role = Role.Button }
                    .testTag("weather_use_current_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (!hasCustomLocation) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcon(
                            icon = R.drawable.ic_location,
                            tint = if (!hasCustomLocation) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 18.dp
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.weather_use_current_location),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.weather_use_current_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = autoContent.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!hasCustomLocation) {
                        AppIcon(
                            icon = R.drawable.ic_check,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 18.dp
                        )
                    }
                }
            }

            if (searching) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                    )
                    Text(
                        text = stringResource(R.string.weather_location_searching),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (results.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(results, key = { idx, city -> "${idx}_${city.name}_${city.latitude}_${city.longitude}" }) { index, city ->
                        val isCurrentCity = hasCustomLocation && currentLocationName.equals(city.fullName, ignoreCase = true)
                        Surface(
                            onClick = {
                                focus.clearFocus()
                                onSelectCity(city)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isCurrentCity) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = if (isCurrentCity) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = city.fullName
                                }
                                .testTag("weather_city_result_$index")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AppIcon(
                                    icon = R.drawable.ic_location,
                                    tint = if (isCurrentCity) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    size = 18.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = city.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (city.displaySubtitle.isNotBlank()) {
                                        Text(
                                            text = city.displaySubtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (isCurrentCity) {
                                    AppIcon(
                                        icon = R.drawable.ic_check,
                                        tint = MaterialTheme.colorScheme.primary,
                                        size = 18.dp
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (!searching && query.trim().length >= 2) {
                Spacer(Modifier.heightIn(min = 4.dp))
                Text(
                    text = stringResource(R.string.weather_no_results),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
        }
    }
}
