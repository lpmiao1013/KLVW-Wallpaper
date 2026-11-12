package com.klvw.wallpaper.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.klvw.wallpaper.tile.KLVWPopupViewModel
import com.klvw.wallpaper.ui.viewmodel.WallpaperViewModel

private enum class NavTab(val label: String, val icon: ImageVector) {
    WALLPAPER("壁纸", Icons.Default.Wallpaper),
    FOLDERS("文件夹", Icons.Default.Folder),
    SETTINGS("设置", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: WallpaperViewModel = hiltViewModel(),
    popupViewModel: KLVWPopupViewModel = hiltViewModel()
) {
    var currentTab by remember { mutableStateOf(NavTab.WALLPAPER) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentTab) {
                            NavTab.WALLPAPER -> "KLVW 壁纸"
                            NavTab.FOLDERS -> "文件夹管理"
                            NavTab.SETTINGS -> "设置"
                        },
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                tonalElevation = 0.dp
            ) {
                NavTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            AnimatedContent(targetState = currentTab, label = "tab_anim") { tab ->
                when (tab) {
                    NavTab.WALLPAPER -> WallpaperPickerScreen(viewModel)
                    NavTab.FOLDERS -> FolderManagerScreen(viewModel)
                    NavTab.SETTINGS -> SettingsScreen(viewModel, popupViewModel)
                }
            }
        }
    }
}
