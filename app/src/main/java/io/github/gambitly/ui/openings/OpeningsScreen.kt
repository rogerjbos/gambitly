package io.github.gambitly.ui.openings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gambitly.ui.common.AppTopBar

@Composable
fun OpeningsScreen(onBack: () -> Unit, onOpen: (Int) -> Unit, viewModel: OpeningsViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val browsingFamily = ui.family != null && ui.query.isBlank()
    BackHandler(enabled = browsingFamily) { viewModel.openFamily(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (browsingFamily) ui.family!! else "Openings",
                onBack = { if (browsingFamily) viewModel.openFamily(null) else onBack() },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!browsingFamily) {
                OutlinedTextField(
                    value = viewModel.searchText,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("Search by name or ECO code") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (ui.query.isBlank() && ui.family == null) {
                    items(ui.families, key = { it.family }) { family ->
                        ListItem(
                            headlineContent = { Text(family.family) },
                            supportingContent = {
                                Text(if (family.lines == 1) "1 line" else "${family.lines} lines")
                            },
                            modifier = Modifier.clickable { viewModel.openFamily(family.family) },
                        )
                        HorizontalDivider()
                    }
                } else {
                    if (ui.lines.isEmpty() && ui.query.isNotBlank()) {
                        item {
                            Text(
                                "No openings match “${ui.query}”.",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    items(ui.lines, key = { it.id }) { line ->
                        ListItem(
                            headlineContent = { Text(if (browsingFamily) line.variation else line.name) },
                            supportingContent = { Text("${line.eco} · ${(line.plies + 1) / 2} moves") },
                            trailingContent = {
                                if (line.id in ui.completed) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "Completed",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            modifier = Modifier.clickable { onOpen(line.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
