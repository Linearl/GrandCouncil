package com.grandcouncil.remote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 主题体系（对标 reasonix TUI 8 套主题，cli/theme.go）：
 * dark 系 graphite/ember/aurora/midnight + light 系 sandstone/porcelain/linen/glacier。
 * App 取 4 套代表（每套含明暗两模式），默认暖色：light=sandstone（#c2613f）、dark=graphite（#d97757）。
 * 状态色语义沿用 reasonix：success #74b87a / warn #d9a441 / err #e5484d / info #56b6c2。
 */
enum class ThemePreset(val label: String, val description: String) {
    /** 暖陶土（reasonix dark 默认主题） */
    WARM("暖阳", "warm clay accent · reasonix 默认"),
    /** 冷青（reasonix aurora） */
    TEAL("青瓷", "cool teal accent"),
    /** 柔紫（reasonix porcelain/midnight） */
    VIOLET("暮紫", "soft violet accent"),
    /** 冷蓝（reasonix glacier） */
    GLACIER("冰川", "cool blue accent"),
}

/** 单套主题的明暗色板（reasonix cliPalette 字段映射） */
data class ReasonixPalette(
    val accent: Color,
    val muted: Color,
    val faint: Color,
    val subtle: Color,
    val success: Color,
    val warn: Color,
    val err: Color,
    val danger: Color,
    val info: Color,
    val secondary: Color,
    val border: Color,
    val selection: Color,
    val userBubble: Color,
    val diffAdd: Color,
    val diffDel: Color,
    val toolRead: Color,
    val toolProc: Color,
)

private fun hex(h: String) = Color(android.graphics.Color.parseColor(h))

/** 4 套主题 × 明暗（颜色值直接来自 DeepSeek-Reasonix internal/cli/theme.go） */
val ReasonixPalettes: Map<ThemePreset, Pair<ReasonixPalette, ReasonixPalette>> = mapOf(
    // 暖阳：graphite(dark) + sandstone(light)
    ThemePreset.WARM to Pair(
        ReasonixPalette(
            accent = hex("#d97757"), muted = hex("#c0c4cc"), faint = hex("#858b96"),
            subtle = hex("#a4a9b3"), success = hex("#74b87a"), warn = hex("#d9a441"),
            err = hex("#e0696a"), danger = hex("#e5484d"), info = hex("#56b6c2"),
            secondary = hex("#b18cff"), border = hex("#343945"), selection = hex("#d97757"),
            userBubble = hex("#222631"), diffAdd = hex("#14351d"), diffDel = hex("#3a1619"),
            toolRead = hex("#56b6c2"), toolProc = hex("#c678dd"),
        ),
        ReasonixPalette(
            accent = hex("#c2613f"), muted = hex("#555049"), faint = hex("#82796f"),
            subtle = hex("#6e675e"), success = hex("#4c9a5c"), warn = hex("#b08020"),
            err = hex("#c2453e"), danger = hex("#c2352e"), info = hex("#3a8a94"),
            secondary = hex("#7d63c8"), border = hex("#d8d2c8"), selection = hex("#c2613f"),
            userBubble = hex("#f0ece4"), diffAdd = hex("#e4f4e0"), diffDel = hex("#fbe4e2"),
            toolRead = hex("#3a8a94"), toolProc = hex("#9c4bb8"),
        ),
    ),
    // 青瓷：aurora
    ThemePreset.TEAL to Pair(
        ReasonixPalette(
            accent = hex("#34c3a6"), muted = hex("#c0c4cc"), faint = hex("#858b96"),
            subtle = hex("#a4a9b3"), success = hex("#74b87a"), warn = hex("#d9a441"),
            err = hex("#e0696a"), danger = hex("#e5484d"), info = hex("#56b6c2"),
            secondary = hex("#b18cff"), border = hex("#2a3f3c"), selection = hex("#34c3a6"),
            userBubble = hex("#1e2b2a"), diffAdd = hex("#14351d"), diffDel = hex("#3a1619"),
            toolRead = hex("#56b6c2"), toolProc = hex("#c678dd"),
        ),
        ReasonixPalette(
            accent = hex("#2a9d8f"), muted = hex("#555049"), faint = hex("#82796f"),
            subtle = hex("#6e675e"), success = hex("#4c9a5c"), warn = hex("#b08020"),
            err = hex("#c2453e"), danger = hex("#c2352e"), info = hex("#3a8a94"),
            secondary = hex("#7d63c8"), border = hex("#d0ddd9"), selection = hex("#2a9d8f"),
            userBubble = hex("#e8f4f1"), diffAdd = hex("#e4f4e0"), diffDel = hex("#fbe4e2"),
            toolRead = hex("#3a8a94"), toolProc = hex("#9c4bb8"),
        ),
    ),
    // 暮紫：midnight(dark) + porcelain(light)
    ThemePreset.VIOLET to Pair(
        ReasonixPalette(
            accent = hex("#b18cff"), muted = hex("#c0c4cc"), faint = hex("#858b96"),
            subtle = hex("#a4a9b3"), success = hex("#74b87a"), warn = hex("#d9a441"),
            err = hex("#e0696a"), danger = hex("#e5484d"), info = hex("#56b6c2"),
            secondary = hex("#34c3a6"), border = hex("#332f42"), selection = hex("#b18cff"),
            userBubble = hex("#232033"), diffAdd = hex("#14351d"), diffDel = hex("#3a1619"),
            toolRead = hex("#56b6c2"), toolProc = hex("#c678dd"),
        ),
        ReasonixPalette(
            accent = hex("#7d63c8"), muted = hex("#555049"), faint = hex("#82796f"),
            subtle = hex("#6e675e"), success = hex("#4c9a5c"), warn = hex("#b08020"),
            err = hex("#c2453e"), danger = hex("#c2352e"), info = hex("#3a8a94"),
            secondary = hex("#2a9d8f"), border = hex("#d8d2e0"), selection = hex("#7d63c8"),
            userBubble = hex("#f0edf6"), diffAdd = hex("#e4f4e0"), diffDel = hex("#fbe4e2"),
            toolRead = hex("#3a8a94"), toolProc = hex("#9c4bb8"),
        ),
    ),
    // 冰川：glacier
    ThemePreset.GLACIER to Pair(
        ReasonixPalette(
            accent = hex("#4a9fd8"), muted = hex("#c0c4cc"), faint = hex("#858b96"),
            subtle = hex("#a4a9b3"), success = hex("#74b87a"), warn = hex("#d9a441"),
            err = hex("#e0696a"), danger = hex("#e5484d"), info = hex("#56b6c2"),
            secondary = hex("#b18cff"), border = hex("#2a3540"), selection = hex("#4a9fd8"),
            userBubble = hex("#1e2630"), diffAdd = hex("#14351d"), diffDel = hex("#3a1619"),
            toolRead = hex("#56b6c2"), toolProc = hex("#c678dd"),
        ),
        ReasonixPalette(
            accent = hex("#357fa8"), muted = hex("#555049"), faint = hex("#82796f"),
            subtle = hex("#6e675e"), success = hex("#4c9a5c"), warn = hex("#b08020"),
            err = hex("#c2453e"), danger = hex("#c2352e"), info = hex("#3a8a94"),
            secondary = hex("#7d63c8"), border = hex("#d0d9e0"), selection = hex("#357fa8"),
            userBubble = hex("#e8f0f6"), diffAdd = hex("#e4f4e0"), diffDel = hex("#fbe4e2"),
            toolRead = hex("#3a8a94"), toolProc = hex("#9c4bb8"),
        ),
    ),
)

/** 卡片信息密度（用户可选，默认清爽） */
enum class DensityPreset(val label: String) {
    COMFORTABLE("清爽"),
    COMPACT("紧凑"),
}

@Composable
fun GrandCouncilTheme(
    preset: ThemePreset = ThemePreset.WARM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = (if (darkTheme) ReasonixPalettes[preset]?.first
    else ReasonixPalettes[preset]?.second) ?: ReasonixPalettes.getValue(ThemePreset.WARM).first

    val colorScheme = if (darkTheme) {
        // P2 AMOLED 纯黑：暗色下背景/表面强制纯黑（OLED 省电 + 对比度）
        val bg = if (amoledDark) Color.Black else hex("#14171c")
        val surface = if (amoledDark) Color.Black else hex("#1a1e24")
        val surfaceVariant = if (amoledDark) hex("#0f0f0f") else hex("#232830")
        val container = if (amoledDark) hex("#0a0a0a") else hex("#1e232a")
        val containerHigh = if (amoledDark) hex("#141414") else hex("#252b33")
        val containerHighest = if (amoledDark) hex("#1a1a1a") else hex("#2c333c")
        darkColorScheme(
            primary = palette.accent,
            onPrimary = if (darkTheme) Color.White else Color.White,
            primaryContainer = palette.userBubble,
            onPrimaryContainer = palette.muted,
            secondary = palette.secondary,
            onSecondary = Color.White,
            secondaryContainer = palette.diffDel,
            onSecondaryContainer = palette.muted,
            tertiary = palette.info,
            background = bg,
            onBackground = if (darkTheme) palette.muted else hex("#33271a"),
            surface = surface,
            onSurface = if (darkTheme) palette.muted else hex("#33271a"),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = if (darkTheme) palette.faint else hex("#6b5d4e"),
            surfaceContainer = container,
            surfaceContainerHigh = containerHigh,
            surfaceContainerHighest = containerHighest,
            outline = palette.border,
            outlineVariant = if (darkTheme) hex("#2c333c") else hex("#eee5d8"),
            error = palette.danger,
            errorContainer = hex("#4a1a1e"),
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            primaryContainer = palette.userBubble,
            onPrimaryContainer = palette.muted,
            secondary = palette.secondary,
            onSecondary = Color.White,
            secondaryContainer = palette.diffDel,
            onSecondaryContainer = palette.muted,
            tertiary = palette.info,
            background = hex("#faf7f2"),
            onBackground = hex("#33271a"),
            surface = Color.White,
            onSurface = hex("#33271a"),
            surfaceVariant = hex("#f6f1e8"),
            onSurfaceVariant = hex("#6b5d4e"),
            surfaceContainer = hex("#fffdf9"),
            surfaceContainerHigh = hex("#f7f2ea"),
            surfaceContainerHighest = hex("#f0e8dc"),
            outline = palette.border,
            outlineVariant = hex("#eee5d8"),
            error = palette.danger,
            errorContainer = hex("#fbe4e2"),
            onError = Color.White,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/** reasonix 语义色扩展：状态点/徽章/工具卡片用（与 TUI 同色） */
object ReasonixColors {
    val success = hex("#74b87a")
    val warn = hex("#d9a441")
    val err = hex("#e5484d")
    val info = hex("#56b6c2")
    val secondary = hex("#b18cff")
    val toolProc = hex("#c678dd")
}
