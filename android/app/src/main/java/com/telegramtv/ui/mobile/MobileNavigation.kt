package com.telegramtv.ui.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.telegramtv.ui.mobile.auth.MobileLoginScreen
import com.telegramtv.ui.mobile.home.MobileHomeScreen
import com.telegramtv.ui.mobile.player.MobilePlayerScreen
import com.telegramtv.ui.mobile.search.MobileSearchScreen
import com.telegramtv.ui.mobile.library.MobileLibraryScreen
import com.telegramtv.ui.mobile.more.MobileMoreScreen
import com.telegramtv.ui.mobile.more.MoreSubScreen
import androidx.compose.material3.MaterialTheme

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : BottomNavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Search : BottomNavItem("search", "Search", Icons.Filled.Search, Icons.Outlined.Search)
    object Library : BottomNavItem("library", "Library", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary)
    object More : BottomNavItem("more", "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz)
    object Movies : BottomNavItem("movies", "Movies", Icons.Filled.Movie, Icons.Outlined.Movie)
    object Series : BottomNavItem("series", "Series", Icons.Filled.Tv, Icons.Outlined.Tv)
    object Actors : BottomNavItem("actors", "Actors", Icons.Filled.People, Icons.Outlined.People)
    object Statistics : BottomNavItem("stats", "Stats", Icons.Filled.BarChart, Icons.Outlined.BarChart)
    object Settings : BottomNavItem("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileScaffold(
    startDestination: String = "dashboard"
) {
    val rootNavController = rememberNavController()

    NavHost(
        navController = rootNavController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Login Screen (Full Screen)
        composable("login") {
            MobileLoginScreen(
                onLoginSuccess = {
                    rootNavController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // 2. Dashboard (Tabs with Scaffold)
        composable("dashboard") {
            MainAppScreen(
                onNavigateToPlayer = { fileId ->
                    rootNavController.navigate("player/$fileId")
                },
                onLogout = {
                    rootNavController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        // 3. Player (True Full Screen)
        composable(
            route = "player/{fileId}",
            arguments = listOf(navArgument("fileId") { type = NavType.IntType })
        ) {
            MobilePlayerScreen(
                onBack = { rootNavController.popBackStack() }
            )
        }
    }
}

@Composable
fun MainAppScreen(
    onNavigateToPlayer: (Int) -> Unit,
    onLogout: () -> Unit
) {
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        val navHost: @Composable () -> Unit = {
            NavHost(
                navController = tabNavController,
                startDestination = BottomNavItem.Home.route + "?folderId={folderId}&folderName={folderName}",
                modifier = Modifier.fillMaxSize()
            ) {
                composable(
                    route = BottomNavItem.Home.route + "?folderId={folderId}&folderName={folderName}",
                    arguments = listOf(
                        navArgument("folderId") { 
                            type = NavType.IntType
                            defaultValue = -1
                        },
                        navArgument("folderName") { 
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) {
                    MobileHomeScreen(
                        onPlayFile = onNavigateToPlayer,
                        onLogout = onLogout,
                        onSearchClick = {
                            tabNavController.navigate(BottomNavItem.Search.route) {
                                popUpTo(tabNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToTab = { targetRoute ->
                            tabNavController.navigate(targetRoute) {
                                popUpTo(tabNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(BottomNavItem.Search.route) {
                    MobileSearchScreen(
                        onPlayFile = onNavigateToPlayer,
                        onGoToFolder = { folderId, folderName ->
                            tabNavController.navigate(BottomNavItem.Home.route + "?folderId=$folderId&folderName=$folderName") {
                                popUpTo(tabNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(BottomNavItem.Library.route) {
                    MobileLibraryScreen(
                        onPlayFile = onNavigateToPlayer
                    )
                }

                composable(BottomNavItem.Movies.route) {
                    MobileMoreScreen(
                        initialSubScreen = MoreSubScreen.MOVIES,
                        onPlayFile = onNavigateToPlayer,
                        onLogout = onLogout
                    )
                }

                composable(BottomNavItem.Series.route) {
                    MobileMoreScreen(
                        initialSubScreen = MoreSubScreen.SERIES,
                        onPlayFile = onNavigateToPlayer,
                        onLogout = onLogout
                    )
                }

                composable(BottomNavItem.Actors.route) {
                    MobileMoreScreen(
                        initialSubScreen = MoreSubScreen.ACTORS,
                        onPlayFile = onNavigateToPlayer,
                        onLogout = onLogout
                    )
                }

                composable(BottomNavItem.Statistics.route) {
                    MobileMoreScreen(
                        initialSubScreen = MoreSubScreen.STATISTICS,
                        onPlayFile = onNavigateToPlayer,
                        onLogout = onLogout
                    )
                }

                composable(BottomNavItem.Settings.route) {
                    MobileMoreScreen(
                        initialSubScreen = MoreSubScreen.SETTINGS,
                        onPlayFile = onNavigateToPlayer,
                        onLogout = onLogout
                    )
                }
            }
        }

        Row(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            CompactNavigationRail(tabNavController, currentRoute)
            Box(modifier = Modifier.consumeWindowInsets(innerPadding).fillMaxSize()) {
                navHost()
            }
        }
    }
}

@Composable
private fun CompactNavigationRail(navController: NavHostController, currentRoute: String?) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(58.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))
        listOf(
            BottomNavItem.Home,
            BottomNavItem.Search,
            BottomNavItem.Library,
            BottomNavItem.Movies,
            BottomNavItem.Series,
            BottomNavItem.Actors,
            BottomNavItem.Statistics,
            BottomNavItem.Settings
        ).forEach { item ->
            val selected = currentRoute?.startsWith(item.route) == true
            NavigationRailItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(if (selected) item.selectedIcon else item.unselectedIcon, item.title) },
                alwaysShowLabel = false,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                )
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun GlassmorphismBottomNavigation(
    navController: NavHostController,
    currentRoute: String?
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 3.dp,
        modifier = Modifier
    ) {
        val items = listOf(
            BottomNavItem.Home,
            BottomNavItem.Search,
            BottomNavItem.Library,
            BottomNavItem.More
        )

        items.forEach { item ->
            val isSelected = currentRoute?.startsWith(item.route) == true
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.title,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            )
        }
    }
}
