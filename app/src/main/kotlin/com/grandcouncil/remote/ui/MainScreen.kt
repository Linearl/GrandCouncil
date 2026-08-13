package com.grandcouncil.remote.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.grandcouncil.remote.R
import com.grandcouncil.remote.ui.connections.ConnectionScreen
import com.grandcouncil.remote.ui.sessions.SessionListScreen

/**
 * 主界面：Remote Tab（唯一 v1 视图）——
 * 会话列表 + 连接管理两个底部导航页。
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == NavRoutes.SESSIONS,
                    onClick = {
                        navController.navigate(NavRoutes.SESSIONS) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_sessions)) },
                )
                NavigationBarItem(
                    selected = currentRoute == NavRoutes.CONNECTIONS,
                    onClick = {
                        navController.navigate(NavRoutes.CONNECTIONS) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_connections)) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.SESSIONS,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(NavRoutes.SESSIONS) { SessionListScreen() }
            composable(NavRoutes.CONNECTIONS) { ConnectionScreen() }
        }
    }
}

object NavRoutes {
    const val SESSIONS = "sessions"
    const val CONNECTIONS = "connections"
}
