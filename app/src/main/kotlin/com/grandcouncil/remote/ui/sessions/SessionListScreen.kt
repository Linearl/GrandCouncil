package com.grandcouncil.remote.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grandcouncil.remote.R
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.connection.ConnectionStore
import com.grandcouncil.remote.model.HeldBy
import com.grandcouncil.remote.model.RemoteSession
import com.grandcouncil.remote.repository.SessionRepository

/**
 * 会话列表页（M1 验收：经 ReasonixAdapter 显示 GET /sessions 结果）。
 * 顶部为远端连接选择器；列表展示 title/turns/current/heldBy。
 * 会话所有权语义：heldBy=OTHER 的会话只读，不可写。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel = viewModel(
        factory = SessionListViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    var profileMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("会话")
                        if (state.activeProfile != null) {
                            Text(
                                state.activeProfile!!.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (state.activeProfile != null) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.sessions_refresh))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.profiles.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.sessions_no_connection),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                ProfileSelector(
                    profiles = state.profiles,
                    active = state.activeProfile,
                    expanded = profileMenuOpen,
                    onToggle = { profileMenuOpen = !profileMenuOpen },
                    onSelect = {
                        profileMenuOpen = false
                        viewModel.selectProfile(it)
                    },
                )
                if (state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.error != null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.refresh() }) {
                                Text(stringResource(R.string.sessions_retry))
                            }
                        }
                    }
                } else if (state.sessions.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.sessions_empty))
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.sessions, key = { it.id }) { session ->
                            SessionItem(session)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSelector(
    profiles: List<ConnectionProfile>,
    active: ConnectionProfile?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (ConnectionProfile) -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Card(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            ListItem(
                headlineContent = { Text(active?.name ?: "选择连接") },
                supportingContent = {
                    Text("${active?.type?.label.orEmpty()} · ${active?.baseUrl.orEmpty()}")
                },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onToggle) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name) },
                    onClick = { onSelect(profile) },
                )
            }
        }
    }
}

@Composable
private fun SessionItem(session: RemoteSession) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append("${session.turns} 轮")
                        if (session.isCurrent) append(" · 当前")
                        if (session.agent == com.grandcouncil.remote.model.AgentType.REASONIX) append(" · Reasonix")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (session.heldBy == HeldBy.OTHER) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.sessions_held_other),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

/** ViewModel 工厂：注入 ConnectionStore/SessionRepository */
class SessionListViewModelFactory(private val appContext: android.content.Context) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return SessionListViewModel(ConnectionStore(appContext), SessionRepository()) as T
    }
}
