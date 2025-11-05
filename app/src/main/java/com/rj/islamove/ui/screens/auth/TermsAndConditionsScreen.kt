package com.rj.islamove.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rj.islamove.ui.theme.IslamovePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsAndConditionsScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Terms and Conditions",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = "Last Updated: October 25, 2024",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Section 1
            SectionTitle("1. Acceptance of Terms")
            SectionContent(
                "By creating an account and using IslaMove, you agree to be bound by these Terms and Conditions. " +
                        "If you do not agree to these terms, please do not use our services. These terms constitute a legally " +
                        "binding agreement between you and IslaMove."
            )

            // Section 2
            SectionTitle("2. Eligibility and Account Requirements")
            SectionContent(
                "• You must be at least 12 years old to register as a user\n" +
                        "• You must provide accurate, current, and complete information during registration\n" +
                        "• Passengers must upload a valid government-issued ID for verification\n" +
                        "• Students and senior citizens must upload valid Student ID or Senior Citizen ID for discount eligibility\n" +
                        "• Drivers must upload and maintain current copies of:\n" +
                        "  - Valid Driver's License\n" +
                        "  - SJMODA Certification\n" +
                        "  - OR/CR (Official Receipt/Certificate of Registration) of bao-bao vehicle\n" +
                        "• You are responsible for maintaining the confidentiality of your account credentials"
            )

            // Section 3
            SectionTitle("3. ID Verification and Discount Processing")
            SectionContent(
                "• All uploaded IDs will be verified by our admin team within 24-48 hours\n" +
                        "• Student and senior citizen discounts will only apply after successful ID verification\n" +
                        "• IslaMove reserves the right to reject any ID that appears invalid, expired, or fraudulent\n" +
                        "• Providing false or fraudulent identification may result in immediate account suspension and possible legal action\n" +
                        "• You must update your ID documents if they expire"
            )

            // Section 4
            SectionTitle("4. Ride-Sharing Service")
            SectionContent(
                "• IslaMove operates as a ride-sharing platform connecting passengers with bao-bao drivers\n" +
                        "• Multiple passengers may be matched to a single bao-bao ride if traveling in the same direction or route\n" +
                        "• Each passenger pays the fixed municipal fare regardless of ride-sharing arrangements\n" +
                        "• The fixed fare follows the approved fare matrix set by the municipality\n"
//                        "• Fares are non-negotiable and must be paid as displayed in the app"
            )

            // Section 5
            SectionTitle("5. User Responsibilities")

            SubsectionTitle("For Passengers:")
            SectionContent(
                "• Arrive at the pickup point on time\n" +
                        "• Treat drivers and other passengers with respect\n" +
                        "• Pay the correct fare as indicated in the app\n" +
                        "• Report any issues or incidents immediately through the app\n" +
                        "• Do not bring prohibited items or engage in illegal activities"
            )

            SubsectionTitle("For Drivers:")
            SectionContent(
                "• Maintain a valid driver's license and all required certifications\n" +
                        "• Keep your bao-bao vehicle in safe, roadworthy condition\n" +
                        "• Follow all traffic laws and regulations\n" +
                        "• Accept ride requests fairly and without discrimination\n" +
                        "• Complete rides along the specified routes\n" +
                        "• Treat all passengers with respect and professionalism\n" +
                        "• Charge only the municipal-approved fixed fare\n" +
                        "• Report any incidents immediately through the app"
            )

            // Section 6
            SectionTitle("6. Prohibited Conduct")
            SectionContent(
                "Users are strictly prohibited from:\n" +
                        "• Using the service for illegal purposes\n" +
                        "• Harassing, threatening, or discriminating against other users\n" +
                        "• Attempting to defraud IslaMove or other users\n" +
                        "• Sharing account credentials with others\n" +
                        "• Manipulating or circumventing the fare system\n" +
                        "• Uploading false or fraudulent documents\n" +
                        "• Damaging property or causing harm to others\n" +
                        "• Using the platform for commercial purposes without authorization"
            )

            // Section 7
            SectionTitle("7. Liability and Disclaimers")
            SectionContent(
                "• IslaMove acts as a technology platform connecting passengers and drivers\n" +
                        "• IslaMove is not a transportation carrier and does not provide transportation services\n" +
                        "• Drivers are independent contractors, not employees of IslaMove\n" +
                        "• IslaMove is not liable for any damages, injuries, or losses occurring during rides\n" +
                        "• Users assume all risks associated with using the transportation services\n" +
                        "• IslaMove does not guarantee availability of rides or drivers at all times\n" +
                        "• We do not guarantee the accuracy of user-provided information"
            )

            // Section 8
            SectionTitle("8. Insurance and Safety")
            SectionContent(
                "• Drivers must maintain appropriate insurance coverage for their vehicles\n" +
                        "• IslaMove recommends but does not guarantee that drivers have insurance\n" +
                        "• Users should verify driver credentials before entering a vehicle\n" +
                        "• Report any safety concerns immediately to local authorities and through the app"
            )

            // Section 9
            SectionTitle("9. Cancellations and Refunds")
            SectionContent(
                "• Cancellation policies are subject to municipal regulations\n" +
                        "• Multiple cancellations may result in account restrictions\n" +
                        "• Refunds, if applicable, will be processed according to our refund policy\n" +
                        "• Disputes must be reported within 24 hours of the incident"
            )

            // Section 10
            SectionTitle("10. Account Suspension and Termination")
            SectionContent(
                "IslaMove reserves the right to suspend or terminate accounts that:\n" +
                        "• Violate these Terms and Conditions\n" +
                        "• Engage in fraudulent activity\n" +
                        "• Receive multiple complaints from other users\n" +
                        "• Fail ID verification\n" +
                        "• Pose safety risks to other users\n" +
                        "• Remain inactive for extended periods"
            )

            // Section 11
            SectionTitle("11. Fare System and Payments")
            SectionContent(
                "• All fares are based on the municipality-approved fare matrix\n" +
                        "• Fares are fixed and non-negotiable\n" +
                        "• Discounts for students and seniors apply only after ID verification\n" +
                        "• Payment methods accepted are displayed in the app\n" +
                        "• IslaMove may charge service fees as disclosed in the app\n" +
                        "• Drivers must not demand payment outside the app"
            )

            // Section 12
            SectionTitle("12. Intellectual Property")
            SectionContent(
                "• All content, features, and functionality of IslaMove are owned by IslaMove and protected by copyright, trademark, and other intellectual property laws\n" +
                        "• You may not copy, modify, distribute, or reverse engineer any part of our service"
            )

            // Section 13
            SectionTitle("13. Changes to Terms")
            SectionContent(
                "• IslaMove reserves the right to modify these Terms and Conditions at any time\n" +
                        "• Users will be notified of significant changes\n" +
                        "• Continued use of the service after changes constitutes acceptance of the new terms"
            )

            // Section 14
            SectionTitle("14. Governing Law")
            SectionContent(
                "These Terms and Conditions are governed by the laws of the Republic of the Philippines. " +
                        "Any disputes shall be resolved in the appropriate courts within the jurisdiction where IslaMove operates."
            )

            // Section 15
            SectionTitle("15. Contact Information")
            SectionContent(
                "For questions about these Terms and Conditions, please contact us at:\n" +
                        "Email: islamoveapp@gmail.com\n" +
//                        "Phone: 09089848952\n" +
                        "Address: Sta. Cruz, San Jose, PDI"
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "By clicking \"I agree to the Terms and Conditions\" during registration, you acknowledge that " +
                        "you have read, understood, and agree to be bound by these terms.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = IslamovePrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
    )
}

@Composable
private fun SubsectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
    )
}

@Composable
private fun SectionContent(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 20.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}