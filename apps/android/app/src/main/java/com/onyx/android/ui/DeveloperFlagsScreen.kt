@file:Suppress("FunctionName")

package com.onyx.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.onyx.android.config.FeatureFlag
import com.onyx.android.config.FeatureFlagStore

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DeveloperFlagsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val flagStore = remember(context) { FeatureFlagStore.getInstance(context) }
    val flagStates =
        remember(flagStore) {
            mutableStateMapOf<FeatureFlag, Boolean>().apply {
                FeatureFlag.entries.forEach { flag ->
                    this[flag] = flagStore.get(flag)
                }
            }
        }

    Scaffold(
        topBar = {
            DeveloperFlagsTopBar(onNavigateBack = onNavigateBack)
        },
    ) { paddingValues ->
        DeveloperFlagsList(
            paddingValues = paddingValues,
            flagStore = flagStore,
            flagStates = flagStates,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DeveloperFlagsTopBar(onNavigateBack: () -> Unit) {
    TopAppBar(
        title = { Text(text = "Developer Flags") },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
    )
}

@Composable
private fun DeveloperFlagsList(
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    flagStore: FeatureFlagStore,
    flagStates: MutableMap<FeatureFlag, Boolean>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
    ) {
        items(FeatureFlag.entries) { flag ->
            FlagItem(flag = flag, flagStore = flagStore, flagStates = flagStates)
            HorizontalDivider()
        }
    }
}

@Composable
private fun FlagItem(
    flag: FeatureFlag,
    flagStore: FeatureFlagStore,
    flagStates: MutableMap<FeatureFlag, Boolean>,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = flag.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Switch(
                checked = flagStates[flag] ?: flag.defaultValue,
                onCheckedChange = { enabled ->
                    flagStore.set(flag, enabled)
                    flagStates[flag] = enabled
                },
            )
        }
        Text(
            text = "Key: ${flag.key}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Default: ${flag.defaultValue}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
