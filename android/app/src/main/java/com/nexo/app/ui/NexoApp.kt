package com.nexo.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.Gym
import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.UserRole
import com.nexo.app.domain.model.canManageGym
import com.nexo.app.ui.admin.ManageScreen
import com.nexo.app.ui.gym.GymSwitcherSheet
import com.nexo.app.ui.home.GymHomeScreen
import com.nexo.app.ui.logbook.LogbookScreen
import com.nexo.app.ui.profile.ProfileScreen
import com.nexo.app.ui.schedule.ClassDetailScreen
import com.nexo.app.ui.schedule.ScheduleScreen

private enum class NexoDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    SCHEDULE("schedule", "Schedule", Icons.Filled.CalendarMonth),
    LOGBOOK("logbook", "Logbook", Icons.AutoMirrored.Filled.MenuBook),
    MANAGE("manage", "Manage", Icons.Filled.AdminPanelSettings),
    PROFILE("profile", "Profile", Icons.Filled.Person)
}

/**
 * The authenticated app shell — bottom navigation switching between the 4
 * main tabs, each backed by [repository]/[gymId] from the current session.
 * Each tab's `ViewModel` is keyed on [gymId] (`viewModel(key = gymId)`) so
 * [onSwitchGym] recreates all of them with the new gym's data immediately,
 * rather than leaving a stale ViewModel instance from the previous gym
 * alive under the same nav back-stack entry.
 */
@Composable
fun NexoApp(
    repository: BackendRepository,
    gymId: String,
    myGyms: List<Pair<Gym, UserRole>>,
    platformRole: PlatformRole,
    onSwitchGym: (String) -> Unit,
    onEnterPlatformDashboard: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    var showGymSwitcher by remember { mutableStateOf(false) }
    val currentGym = myGyms.firstOrNull { it.first.id == gymId }?.first
    val gymName = currentGym?.name.orEmpty()
    val workoutTypes = currentGym?.workoutTypes.orEmpty()
    val userRole = myGyms.firstOrNull { it.first.id == gymId }?.second
    val canManage = canManageGym(userRole, platformRole)
    val visibleDestinations = NexoDestination.entries.filter { it != NexoDestination.MANAGE || canManage }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = currentBackStackEntry?.destination

                visibleDestinations.forEach { destination ->
                    val isSelected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NexoDestination.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NexoDestination.HOME.route) {
                GymHomeScreen(
                    repository,
                    gymId,
                    onNavigateToSchedule = {
                        navController.navigate(NexoDestination.SCHEDULE.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenGymSwitcher = { showGymSwitcher = true },
                    onOpenClassDetail = { classId -> navController.navigate("classDetail/$classId") }
                )
            }
            composable(NexoDestination.SCHEDULE.route) {
                ScheduleScreen(
                    repository,
                    gymId,
                    gymName = gymName,
                    workoutTypes = workoutTypes,
                    canManage = canManage,
                    onOpenGymSwitcher = { showGymSwitcher = true },
                    onOpenClassDetail = { classId -> navController.navigate("classDetail/$classId") }
                )
            }
            composable(NexoDestination.LOGBOOK.route) { LogbookScreen(repository, gymId) }
            if (canManage) {
                composable(NexoDestination.MANAGE.route) {
                    ManageScreen(
                        repository,
                        gymId,
                        gym = myGyms.firstOrNull { it.first.id == gymId }?.first,
                        userRole = userRole,
                        platformRole = platformRole
                    )
                }
            }
            composable(NexoDestination.PROFILE.route) { ProfileScreen(repository, gymId, onSignOut = onSignOut) }
            composable(
                route = "classDetail/{classId}",
                arguments = listOf(navArgument("classId") { type = NavType.StringType })
            ) { backStackEntry ->
                val classId = backStackEntry.arguments?.getString("classId")
                if (classId != null) {
                    ClassDetailScreen(
                        repository,
                        gymId,
                        classId,
                        workoutTypes = workoutTypes,
                        canManage = canManage,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    if (showGymSwitcher) {
        GymSwitcherSheet(
            myGyms = myGyms,
            currentGymId = gymId,
            showPlatformDashboardOption = platformRole == PlatformRole.ADMIN,
            onSelectGym = onSwitchGym,
            onSelectPlatformDashboard = onEnterPlatformDashboard,
            onDismiss = { showGymSwitcher = false }
        )
    }
}
