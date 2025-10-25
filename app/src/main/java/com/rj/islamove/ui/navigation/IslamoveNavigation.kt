package com.rj.islamove.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rj.islamove.ui.screens.auth.LoginScreen
import com.rj.islamove.ui.screens.auth.CreateAccountScreen
import com.rj.islamove.ui.screens.auth.UserTypeSelectionScreen
import com.rj.islamove.ui.screens.driver.DriverHomeScreen
import com.rj.islamove.ui.screens.driver.DriverTripDetailsScreen
import com.rj.islamove.ui.screens.driver.EarningsScreen
import com.rj.islamove.ui.screens.passenger.PassengerHomeScreen
import com.rj.islamove.ui.screens.passenger.TripDetailsScreen
import com.rj.islamove.ui.screens.payment.PaymentScreen
import com.rj.islamove.ui.screens.profile.ProfileScreen
import com.rj.islamove.ui.screens.profile.ProfileViewModel
import com.rj.islamove.ui.screens.profile.RideHistoryScreen
import com.rj.islamove.ui.screens.driver.DriverDocumentsScreen
import com.rj.islamove.ui.screens.driver.MissedRequestsScreen
import com.rj.islamove.ui.screens.splash.SplashScreen
import com.rj.islamove.ui.screens.rating.RatingScreen
import com.rj.islamove.ui.screens.admin.AdminHomeScreen
import com.rj.islamove.ui.screens.admin.ManageUsersScreen
import com.rj.islamove.ui.screens.admin.UserDetailScreen
import com.rj.islamove.ui.screens.admin.TripHistoryScreen
import com.rj.islamove.ui.screens.admin.ManageUsersViewModel
import com.rj.islamove.ui.screens.admin.DriverVerificationScreen
import com.rj.islamove.ui.screens.admin.DriverDetailsScreen
import com.rj.islamove.ui.screens.admin.DocumentDetailsScreen
import com.rj.islamove.ui.screens.admin.LiveMonitoringScreen
import com.rj.islamove.ui.screens.admin.SystemConfigScreen
import com.rj.islamove.ui.screens.admin.ServiceAreaManagementScreen
import com.rj.islamove.ui.screens.admin.AnalyticsScreen
import com.rj.islamove.ui.screens.admin.FinancialReportsScreen
import com.rj.islamove.ui.screens.reviews.ReviewsScreen
import com.rj.islamove.ui.screens.help.HelpSupportScreen
import com.rj.islamove.ui.screens.onboarding.OnboardingScreen

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object CreateAccount : Screen("create_account")
    object UserTypeSelection : Screen("user_type_selection")
    object PassengerHome : Screen("passenger_home")
    object PassengerTripDetails : Screen("passenger_trip_details/{bookingId}") {
        fun createRoute(bookingId: String) = "passenger_trip_details/$bookingId"
    }
    object DriverHome : Screen("driver_home")
    object DriverTripDetails : Screen("driver_trip_details/{bookingId}") {
        fun createRoute(bookingId: String) = "driver_trip_details/$bookingId"
    }
    object Profile : Screen("profile")
    object HelpSupport : Screen("help_support")
    object RideHistory : Screen("ride_history/{userId}") {
        fun createRoute(userId: String) = "ride_history/$userId"
    }
    object DriverDocuments : Screen("driver_documents/{driverId}") {
        fun createRoute(driverId: String) = "driver_documents/$driverId"
    }
    object Bookings : Screen("bookings")
    object Earnings : Screen("earnings/{driverId}") {
        fun createRoute(driverId: String) = "earnings/$driverId"
    }
    object Payment : Screen("payment/{bookingId}") {
        fun createRoute(bookingId: String) = "payment/$bookingId"
    }
    object RideDetails : Screen("ride_details/{rideId}") {
        fun createRoute(rideId: String) = "ride_details/$rideId"
    }
    object Rating : Screen("rating/{bookingId}/{toUserId}/{toUserType}") {
        fun createRoute(bookingId: String, toUserId: String, toUserType: String) =
            "rating/$bookingId/$toUserId/$toUserType"
    }
    
    // Admin routes
    object AdminHome : Screen("admin_home")
    object ManageUsers : Screen("manage_users")
    object UserDetail : Screen("user_detail/{userId}") {
        fun createRoute(userId: String) = "user_detail/$userId"
    }
    object DriverVerification : Screen("driver_verification")
    object DriverDetails : Screen("driver_details/{driverUid}") {
        fun createRoute(driverUid: String) = "driver_details/$driverUid"
    }
    object DocumentDetails : Screen("document_details/{driverUid}/{documentType}/{documentTitle}") {
        fun createRoute(driverUid: String, documentType: String, documentTitle: String) =
            "document_details/$driverUid/$documentType/$documentTitle"
    }
    object LiveMonitoring : Screen("live_monitoring")
    object TripHistory : Screen("trip_history/{userId}") {
        fun createRoute(userId: String) = "trip_history/$userId"
    }
    object SystemConfig : Screen("system_config")
    object ServiceAreaManagement : Screen("service_area_management")
    object Analytics : Screen("analytics")
    object FinancialReports : Screen("financial_reports")
    object Reports : Screen("reports")
    object MissedRequests : Screen("missed_requests")
    object Reviews : Screen("reviews")
}

@Composable
fun IslamoveNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToHome = { userType ->
                    val destination = when (userType) {
                        "DRIVER" -> Screen.DriverHome.route
                        "ADMIN" -> Screen.AdminHome.route
                        else -> Screen.PassengerHome.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToUserTypeSelection = {
                    navController.navigate(Screen.UserTypeSelection.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onGetStarted = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    // Navigate back to splash to check user type and go to appropriate home
                    navController.navigate(Screen.Splash.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNeedsUserTypeSelection = {
                    // New user needs to select user type
                    navController.navigate(Screen.UserTypeSelection.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToCreateAccount = {
                    navController.navigate(Screen.CreateAccount.route)
                }
            )
        }
        
        composable(Screen.CreateAccount.route) {
            CreateAccountScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onAccountCreated = { userType ->
                    // Navigate directly to appropriate home based on user type
                    val destination = when (userType) {
                        "DRIVER" -> Screen.DriverHome.route
                        "ADMIN" -> Screen.AdminHome.route
                        else -> Screen.PassengerHome.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.CreateAccount.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.UserTypeSelection.route) {
            UserTypeSelectionScreen(
                onUserTypeSelected = { userType ->
                    val destination = when (userType) {
                        "DRIVER" -> Screen.DriverHome.route
                        "ADMIN" -> Screen.AdminHome.route
                        else -> Screen.PassengerHome.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.UserTypeSelection.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.PassengerHome.route) {
            PassengerHomeScreen(
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onNavigateToRating = { bookingId, toUserId, toUserType ->
                    navController.navigate(Screen.Rating.createRoute(bookingId, toUserId, toUserType))
                },
                onNavigateToReviews = {
                    navController.navigate(Screen.Reviews.route)
                },
                onNavigateToDriverDocuments = { userId ->
                    navController.navigate("driver_documents/${userId}?passenger_mode=true")
                },
                onNavigateToTripDetails = { bookingId ->
                    navController.navigate(Screen.PassengerTripDetails.createRoute(bookingId))
                },
                onNavigateToHelpSupport = {
                    navController.navigate(Screen.HelpSupport.route)
                },
                onSignOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.PassengerTripDetails.route) { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
            TripDetailsScreen(
                bookingId = bookingId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.DriverHome.route) {
            DriverHomeScreen(
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onNavigateToEarnings = { driverId ->
                    navController.navigate(Screen.Earnings.createRoute(driverId))
                },
                onNavigateToRating = { bookingId, passengerId ->
                    navController.navigate(Screen.Rating.createRoute(bookingId, passengerId, "PASSENGER"))
                },
                onNavigateToMissedRequests = {
                    navController.navigate(Screen.MissedRequests.route)
                },
                onNavigateToTripDetails = { bookingId ->
                    navController.navigate(Screen.DriverTripDetails.createRoute(bookingId))
                }
            )
        }

        composable(Screen.DriverTripDetails.route) { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
            DriverTripDetailsScreen(
                bookingId = bookingId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
          
        composable("earnings/{driverId}") { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: ""
            EarningsScreen(
                driverId = driverId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("payment/{bookingId}") { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
            PaymentScreen(
                bookingId = bookingId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Profile.route) {
            val profileViewModel: ProfileViewModel = hiltViewModel()

            // Refresh profile data whenever this composable is recomposed
            // This ensures fresh data when returning from EditProfile
            LaunchedEffect(navController.currentBackStackEntry) {
                profileViewModel.loadUserProfile()
            }

            ProfileScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEditProfile = {
                    // EditProfile functionality removed - profile editing is now inline
                },
                onNavigateToRideHistory = { userId ->
                    navController.navigate(Screen.RideHistory.createRoute(userId))
                },
                onNavigateToDriverDocuments = { driverId ->
                    navController.navigate(Screen.DriverDocuments.createRoute(driverId))
                },
                onNavigateToReviews = {
                    navController.navigate(Screen.Reviews.route)
                },
                onNavigateToHelpSupport = {
                    navController.navigate(Screen.HelpSupport.route)
                },
                onSignOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                viewModel = profileViewModel
            )
        }
        
        
        composable("ride_history/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            RideHistoryScreen(
                userId = userId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onRideSelected = { rideId ->
                    navController.navigate(Screen.RideDetails.createRoute(rideId))
                }
            )
        }
        
        composable("driver_documents/{driverId}") { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: ""
            DriverDocumentsScreen(
                driverId = driverId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                isPassengerMode = false
            )
        }

        composable("driver_documents/{driverId}?passenger_mode={passengerMode}") { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: ""
            val passengerMode = backStackEntry.arguments?.getString("passengerMode")?.toBoolean() ?: false
            DriverDocumentsScreen(
                driverId = driverId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                isPassengerMode = passengerMode
            )
        }
        
        composable("rating/{bookingId}/{toUserId}/{toUserType}") { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
            val toUserId = backStackEntry.arguments?.getString("toUserId") ?: ""
            val toUserType = backStackEntry.arguments?.getString("toUserType") ?: "DRIVER"
            RatingScreen(
                navController = navController,
                bookingId = bookingId,
                toUserId = toUserId,
                toUserType = toUserType
            )
        }

        
        
        // Admin routes
        composable(Screen.AdminHome.route) {
            AdminHomeScreen(
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onNavigateToDriverVerification = {
                    navController.navigate(Screen.DriverVerification.route)
                },
                onNavigateToLiveMonitoring = {
                    navController.navigate(Screen.LiveMonitoring.route)
                },
                onNavigateToSystemConfig = {
                    navController.navigate(Screen.SystemConfig.route)
                },
                onNavigateToReports = {
                    navController.navigate(Screen.Analytics.route)
                },
                onNavigateToManageUsers = {
                    navController.navigate(Screen.ManageUsers.route)
                }
            )
        }

        // Manage Users Screen
        composable(Screen.ManageUsers.route) {
            ManageUsersScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToUserDetail = { user ->
                    navController.navigate(Screen.UserDetail.createRoute(user.uid))
                }
            )
        }

        // User Detail Screen
        composable(Screen.UserDetail.route) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable

            // We need to get the user data and comments
            val manageUsersViewModel: ManageUsersViewModel = hiltViewModel()

            // Use real-time listener for user data to show updates immediately
            var user by remember { mutableStateOf<com.rj.islamove.data.models.User?>(null) }

            DisposableEffect(userId) {
                // Real-time listener for user data changes
                val listener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            android.util.Log.e("Navigation", "Error listening to user data", error)
                            return@addSnapshotListener
                        }

                        snapshot?.let {
                            user = it.toObject(com.rj.islamove.data.models.User::class.java)
                            android.util.Log.d("Navigation", "User data updated in real-time")
                        }
                    }

                onDispose {
                    listener.remove()
                }
            }

            if (user == null) {
                return@composable Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Load user comments
            var userComments by remember { mutableStateOf<List<com.rj.islamove.data.models.SupportComment>>(emptyList()) }

            LaunchedEffect(userId, user) {
                userComments = manageUsersViewModel.getUserComments(userId)
                // Load driver reports if this is a driver
                user?.let {
                    if (it.userType == com.rj.islamove.data.models.UserType.DRIVER) {
                        manageUsersViewModel.loadDriverReports(userId)
                    }
                }
            }

            UserDetailScreen(
                user = user!!,
                userComments = userComments,
                driverReports = manageUsersViewModel.uiState.collectAsState().value.driverReports,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onViewTripHistory = {
                    navController.navigate(Screen.TripHistory.createRoute(user!!.uid))
                },
                onNavigateToDocumentDetails = { driverUid, docType, docTitle ->
                    navController.navigate(Screen.DocumentDetails.createRoute(driverUid, docType, docTitle))
                },
                onUpdateDiscount = { userId, discount ->
                    manageUsersViewModel.updateUserDiscount(userId, discount)
                },
                onUpdateVerification = { userId, isVerified ->
                    manageUsersViewModel.updatePassengerVerification(userId, isVerified)
                },
                onUpdatePersonalInfo = { userId, updates ->
                    manageUsersViewModel.updateUserPersonalInfo(userId, updates)
                },
                onUpdateActiveStatus = { userId, isActive ->
                    manageUsersViewModel.updateUserStatus(user!!, isActive)
                },
                onUpdateReportStatus = { reportId, newStatus ->
                    manageUsersViewModel.updateReportStatus(reportId, newStatus)
                },
                onUpdateDriverVerification = { userId, verificationStatus ->
                    manageUsersViewModel.updateDriverVerification(userId, verificationStatus)
                },
                onUpdatePassword = { userId, newPassword ->
                    manageUsersViewModel.updateUserPassword(userId, newPassword)
                },
                onDeleteUser = { user ->
                    manageUsersViewModel.deleteUser(user)
                    navController.popBackStack()
                }
            )
        }

        // Trip History Screen
        composable(Screen.TripHistory.route) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            TripHistoryScreen(
                userId = userId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Driver Verification Screen
        composable(Screen.DriverVerification.route) {
            DriverVerificationScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDriverDetails = { driverUid ->
                    navController.navigate(Screen.DriverDetails.createRoute(driverUid))
                },
                onNavigateToStudentVerification = { studentUid ->
                    navController.navigate("driver_details/${studentUid}?student_verification=true")
                }
            )
        }
        
        // Driver Details Screen
        composable("driver_details/{driverUid}") { backStackEntry ->
            val driverUid = backStackEntry.arguments?.getString("driverUid") ?: ""
            DriverDetailsScreen(
                driverUid = driverUid,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDocumentDetails = { driverUid, docType, docTitle, _ ->
                    navController.navigate(Screen.DocumentDetails.createRoute(driverUid, docType, docTitle))
                },
                isStudentVerification = false
            )
        }

        // Student Verification Screen (using DriverDetailsScreen with student mode)
        composable("driver_details/{driverUid}?student_verification={isStudentVerification}") { backStackEntry ->
            val driverUid = backStackEntry.arguments?.getString("driverUid") ?: ""
            val isStudentVerification = backStackEntry.arguments?.getString("isStudentVerification")?.toBoolean() ?: false
            DriverDetailsScreen(
                driverUid = driverUid,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDocumentDetails = { driverUid, docType, docTitle, _ ->
                    navController.navigate(Screen.DocumentDetails.createRoute(driverUid, docType, docTitle))
                },
                isStudentVerification = isStudentVerification
            )
        }

        // Document Details Screen
        composable("document_details/{driverUid}/{documentType}/{documentTitle}") { backStackEntry ->
            val driverUid = backStackEntry.arguments?.getString("driverUid") ?: ""
            val documentType = backStackEntry.arguments?.getString("documentType") ?: ""
            val documentTitle = backStackEntry.arguments?.getString("documentTitle") ?: ""

            DocumentDetailsScreen(
                driverUid = driverUid,
                documentType = documentType,
                documentTitle = documentTitle,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // Live Monitoring Screen
        composable(Screen.LiveMonitoring.route) {
            LiveMonitoringScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // System Configuration Screen
        composable(Screen.SystemConfig.route) {
            SystemConfigScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToServiceAreaManagement = {
                    navController.navigate(Screen.ServiceAreaManagement.route)
                }
            )
        }
        
        // Service Area Management Screen (Combined Fare + Service Area)
        composable(Screen.ServiceAreaManagement.route) {
            ServiceAreaManagementScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        
        // Analytics Screen
        composable(Screen.Analytics.route) {
            AnalyticsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToFinancialReports = {
                    navController.navigate(Screen.FinancialReports.route)
                },
                onNavigateToUserAnalytics = {
                    // TODO: Navigate to user analytics screen
                },
                onNavigateToOperationalReports = {
                    // TODO: Navigate to operational reports screen
                }
            )
        }
        
        // Financial Reports Screen
        composable(Screen.FinancialReports.route) {
            FinancialReportsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Missed Requests Screen
        composable(Screen.MissedRequests.route) {
            MissedRequestsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = hiltViewModel()
            )
        }

        // Reviews Screen
        composable(Screen.Reviews.route) {
            ReviewsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Help & Support Screen
        composable(Screen.HelpSupport.route) {
            HelpSupportScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}