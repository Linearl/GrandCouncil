package com.grandcouncil.remote.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.grandcouncil.remote.ui.config.ConfigScreen
import com.grandcouncil.remote.ui.sessions.SessionListScreen
import com.grandcouncil.remote.ui.wizard.WizardScreen

/**
 * 主界面（V3 三栏布局）：会话 / 配置（连接+设置）/ 向导。
 * 会话：按设备筛选 + 日期排序 + 只显示本地聊天（筛选 chips）
 * 配置：连接管理（增删改/测试）+ 设置（主题/密度）
 * 向导：分步连接向导（选方式 → 地址 → 认证 → 宿主命令 → 测试保存）
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
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_sessions)) },
                )
                NavigationBarItem(
                    selected = currentRoute == NavRoutes.CONFIG,
                    onClick = {
                        navController.navigate(NavRoutes.CONFIG) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_connections)) },
                )
                NavigationBarItem(
                    selected = currentRoute == NavRoutes.WIZARD,
                    onClick = {
                        navController.navigate(NavRoutes.WIZARD) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_wizard)) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.SESSIONS,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(NavRoutes.SESSIONS) {
                SessionListScreen(
                    onNavigateToConfig = {
                        navController.navigate(NavRoutes.CONFIG) {
                            popUpTo(NavRoutes.SESSIONS) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToWizard = {
                        navController.navigate(NavRoutes.WIZARD) {
                            popUpTo(NavRoutes.SESSIONS) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(NavRoutes.CONFIG) { ConfigScreen() }
            composable(NavRoutes.WIZARD) { WizardScreen() }
        }
    }
}

object NavRoutes {
    const val SESSIONS = "sessions"
    const val CONFIG = "config"
    const val WIZARD = "wizard"
}
