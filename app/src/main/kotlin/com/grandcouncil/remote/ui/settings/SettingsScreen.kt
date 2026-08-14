package com.grandcouncil.remote.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grandcouncil.remote.R
import com.grandcouncil.remote.ui.AppPreferences
import com.grandcouncil.remote.ui.theme.DensityPreset
import com.grandcouncil.remote.ui.theme.ReasonixPalettes
import com.grandcouncil.remote.ui.theme.ThemePreset
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 设置页：主题配色（4 套 × 明暗，对标 reasonix TUI）+ 卡片密度（默认清爽）。
 * 主题/密度持久化于 AppPreferences（DataStore）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    var theme by remember { mutableStateOf(ThemePreset.WARM) }
    var density by remember { mutableStateOf(DensityPreset.COMFORTABLE) }
    var biometricLock by remember { mutableStateOf(false) }
    var amoledDark by remember { mutableStateOf(false) }
    var dynamicColor by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }
    // 联网注入文本编辑（文档 §2.2）
    var webSearchPrompt by remember { mutableStateOf(AppPreferences.DEFAULT_WEB_SEARCH_PROMPT) }
    var webPromptEditOpen by remember { mutableStateOf(false) }
    var webSearchPromptDraft by remember { mutableStateOf(AppPreferences.DEFAULT_WEB_SEARCH_PROMPT) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        prefs.theme.collect { theme = it }
        prefs.density.collect { density = it }
        prefs.biometricLock.collect { biometricLock = it }
        prefs.amoledDark.collect { amoledDark = it }
        prefs.dynamicColor.collect { dynamicColor = it }
        prefs.autoScroll.collect { autoScroll = it }
        prefs.webSearchPrompt.collect { webSearchPrompt = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("主题配色（对标 Reasonix Desktop）")
            ThemePreset.entries.forEach { preset ->
                val (dark, light) = ReasonixPalettes.getValue(preset)
                ThemeRow(
                    preset = preset,
                    accentDark = dark.accent,
                    accentLight = light.accent,
                    selected = theme == preset,
                    onClick = {
                        theme = preset
                        scope.launch { prefs.setTheme(preset) }
                    },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("卡片信息密度")
            DensityPreset.entries.forEach { preset ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = density == preset,
                            onClick = {
                                density = preset
                                scope.launch { prefs.setDensity(preset) }
                            },
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(preset.label, modifier = Modifier.weight(1f))
                    Text(
                        if (preset == DensityPreset.COMFORTABLE) "默认 · 大间距易读" else "小间距 · 一屏更多",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (density == preset) "●" else "○",
                        color = if (density == preset) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("显示")
            // 聊天滚动跟随：生成时自动滚动到底（默认开）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("生成时自动滚动跟随", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (autoScroll) "推理/工具/输出更新时保持底部" else "关闭（发送消息仍滚到底）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoScroll,
                    onCheckedChange = { on ->
                        autoScroll = on
                        scope.launch { prefs.setAutoScroll(on) }
                    },
                )
            }
            // P2 动态取色：Android 12+ 跟随壁纸（Material You）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("跟随壁纸（动态取色）", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (dynamicColor) "使用系统 Material You 配色" else "Android 12+ 生效，默认关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = dynamicColor,
                    onCheckedChange = { on ->
                        dynamicColor = on
                        scope.launch { prefs.setDynamicColor(on) }
                    },
                )
            }
            // P2 AMOLED 纯黑：暗色模式下背景/表面强制纯黑（OLED 省电、对比度高）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("OLED 纯黑模式", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (amoledDark) "暗色背景强制纯黑（省电）" else "仅暗色主题生效，默认关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = amoledDark,
                    onCheckedChange = { on ->
                        amoledDark = on
                        scope.launch { prefs.setAmoledDark(on) }
                    },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("联网与思考")
            // 联网注入文本（文档 §2.2：可自定义；点击编辑）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clickable { webPromptEditOpen = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("联网注入文本", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "发送时追加到消息末尾，引导服务端搜索（点击编辑）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (webSearchPrompt != AppPreferences.DEFAULT_WEB_SEARCH_PROMPT) "已自定义" else "默认",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (webPromptEditOpen) {
                AlertDialog(
                    onDismissRequest = { webPromptEditOpen = false },
                    title = { Text("联网注入文本") },
                    text = {
                        Column {
                            Text(
                                "开启输入栏 🌐 后，每次发送都会在消息末尾追加这段文本。留空恢复默认。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = webSearchPromptDraft,
                                onValueChange = { webSearchPromptDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4,
                                maxLines = 8,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    prefs.setWebSearchPrompt(
                                        webSearchPromptDraft.trim().ifEmpty {
                                            AppPreferences.DEFAULT_WEB_SEARCH_PROMPT
                                        },
                                    )
                                }
                                webPromptEditOpen = false
                            },
                        ) { Text("保存") }
                    },
                    dismissButton = {
                        TextButton(onClick = { webPromptEditOpen = false }) { Text("取消") }
                    },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("安全")
            // A4 生物识别锁开关（打开 App / 回前台时验证身份）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("生物识别锁", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (biometricLock) "打开 App 与返回前台时需指纹/面容验证" else "关闭（凭据仍加密存储于设备 Keystore）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = biometricLock,
                    onCheckedChange = { on ->
                        biometricLock = on
                        scope.launch { prefs.setBiometricLock(on) }
                    },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("关于")
            Text(
                "GrandCouncil v0.1 · Apache-2.0\n主题配色对标 reasonix serve TUI（cli/theme.go 8 套主题）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun ThemeRow(
    preset: ThemePreset,
    accentDark: androidx.compose.ui.graphics.Color,
    accentLight: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = accentLight,
            modifier = Modifier.padding(end = 8.dp),
        ) {
            Text(
                "A",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = accentDark,
            modifier = Modifier.padding(end = 12.dp),
        ) {
            Text(
                "A",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(preset.label, style = MaterialTheme.typography.titleSmall)
            Text(
                preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (selected) "●" else "○",
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
        )
    }
}
