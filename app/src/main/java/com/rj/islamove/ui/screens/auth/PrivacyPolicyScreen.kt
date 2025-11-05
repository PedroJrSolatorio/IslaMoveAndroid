package com.rj.islamove.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rj.islamove.ui.theme.IslamovePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Privacy Policy",
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

            Text(
                text = "IslaMove (\"we,\" \"our,\" or \"us\") is committed to protecting your privacy. This Privacy Policy " +
                        "explains how we collect, use, disclose, and safeguard your information when you use our mobile application " +
                        "and services. This policy complies with the Data Privacy Act of 2012 (Republic Act No. 10173) of the Philippines.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Section 1
            SectionTitle("1. Information We Collect")

            SubsectionTitle("Personal Information")
            SectionContent(
                "When you register for IslaMove, we collect:\n" +
                        "• Full name (first name and last name)\n" +
                        "• Email address\n" +
                        "• Phone number\n" +
                        "• Date of birth\n" +
                        "• Gender\n" +
                        "• Complete residential address\n" +
                        "• Password (encrypted)"
            )

            SubsectionTitle("Identity Verification Documents")
            SectionContent(
                "For Passengers:\n" +
                        "• Government-issued ID (e.g., National ID, Driver's License, Passport, etc.)\n" +
                        "• Student ID (for student discounts)\n" +
                        "• Senior Citizen ID (for senior discounts)\n\n" +
                        "For Drivers:\n" +
                        "• Driver's License\n" +
                        "• Franchise Certificate\n" +
                        "• OR/CR (Official Receipt/Certificate of Registration) of bao-bao vehicle\n" +
                        "• Vehicle information"
            )

            SubsectionTitle("Location Information")
            SectionContent(
                "• Real-time GPS location during rides\n" +
                        "• Pickup and drop-off locations\n" +
                        "• Route information\n" +
                        "• Location history for service improvement"
            )

            SubsectionTitle("Transaction Information")
            SectionContent(
                "• Ride history and details\n" +
                        "• Payment information\n" +
                        "• Fare amounts\n" +
                        "• Discount applications\n" +
                        "• Cancellation records"
            )

            SubsectionTitle("Device Information")
            SectionContent(
                "• Device type and model\n" +
                        "• Operating system\n" +
                        "• IP address\n" +
                        "• Mobile network information\n" +
                        "• App usage data"
            )

            SubsectionTitle("Communications")
            SectionContent(
                "• Messages between users (passengers and drivers)\n" +
                        "• Customer support communications\n" +
                        "• Feedback and ratings\n" +
                        "• Complaints and incident reports"
            )

            // Section 2
            SectionTitle("2. How We Use Your Information")
            SectionContent(
                "We use your information for the following purposes:\n\n" +
                        "• To create and manage your account\n" +
                        "• To verify your identity and uploaded documents\n" +
                        "• To facilitate ride matching between passengers and drivers\n" +
                        "• To process payments and apply appropriate discounts\n" +
                        "• To enable ride-sharing when passengers travel in the same direction\n" +
                        "• To calculate fares based on the municipal fare matrix\n" +
                        "• To provide real-time ride tracking and navigation\n" +
                        "• To communicate important service updates\n" +
                        "• To ensure safety and security of users\n" +
                        "• To prevent fraud and unauthorized activities\n" +
                        "• To comply with legal obligations\n" +
                        "• To improve our services and user experience\n" +
                        "• To provide customer support\n" +
                        "• To resolve disputes and enforce our Terms and Conditions\n" +
                        "• To send promotional materials (with your consent)"
            )

            // Section 3
            SectionTitle("3. Legal Basis for Processing (Data Privacy Act Compliance)")
            SectionContent(
                "We process your personal data based on:\n\n" +
                        "• Consent: You provide explicit consent when creating an account and accepting our policies\n" +
                        "• Contract: Processing is necessary to fulfill our services to you\n" +
                        "• Legal Obligation: Compliance with transportation regulations and ID verification requirements\n" +
                        "• Legitimate Interest: Fraud prevention, safety, and service improvement"
            )

            // Section 4
            SectionTitle("4. How We Share Your Information")
            SectionContent(
                "We may share your information with:\n\n" +
                        "• Drivers and Passengers: Limited information necessary for ride coordination (name, pickup/drop-off location, phone number)\n" +
                        "• Service Providers: Third-party vendors who help us operate our platform (e.g., cloud storage, database platform)\n" +
                        "• Government Authorities: When required by law or to comply with legal processes\n" +
                        "• Law Enforcement: To report suspected illegal activity or respond to legal requests\n" +
                        "• Emergency Services: In case of accidents or emergencies\n" +
                        "• Municipal Transportation Office: To comply with local transportation regulations\n\n" +
                        "We do NOT sell your personal information to third parties."
            )

            // Section 5
            SectionTitle("5. Data Storage and Security")
            SectionContent(
                "• Your data is stored on secure servers with encryption\n" +
                        "• We implement industry-standard security measures including:\n" +
                        "  - Encrypted data transmission (SSL/TLS)\n" +
                        "  - Secure password storage (hashing and salting)\n" +
                        "  - Regular security audits\n" +
                        "  - Access controls and authentication\n" +
                        "  - Firewall protection\n" +
                        "• ID documents are stored securely with restricted admin-only access\n" +
                        "• We retain your data only as long as necessary for service provision and legal compliance"
            )

            // Section 6
            SectionTitle("6. ID Verification Process")
            SectionContent(
                "• Uploaded IDs are reviewed manually by authorized admin personnel only\n" +
                        "• Verification typically takes 24-48 hours\n" +
                        "• IDs are used solely for identity verification and discount eligibility\n" +
                        "• We verify that IDs are valid, not expired, and match the user's provided information\n" +
                        "• Student and senior citizen IDs must be current to maintain discount eligibility\n" +
                        "• Failed verification may result in account restrictions until valid documents are provided\n" +
                        "• You will be notified of verification results via email or in-app notification"
            )

            // Section 7
            SectionTitle("7. Your Data Privacy Rights")
            SectionContent(
                "Under the Data Privacy Act of 2012, you have the right to:\n\n" +
                        "• Access: Request copies of your personal data\n" +
                        "• Correction: Request correction of inaccurate information\n" +
                        "• Erasure: Request deletion of your data (subject to legal retention requirements)\n" +
                        "• Object: Object to processing of your data for specific purposes\n" +
                        "• Data Portability: Request transfer of your data to another service\n" +
                        "• Withdraw Consent: Withdraw consent for data processing (may affect service availability)\n" +
                        "• Lodge Complaints: File complaints with the National Privacy Commission\n\n" +
                        "To exercise these rights, contact us at rjsolatorio@gmail.com"
            )

            // Section 8
            SectionTitle("8. Location Data and Tracking")
            SectionContent(
                "• We collect real-time location data when you use our service\n" +
                        "• Location tracking enables ride matching, navigation, and safety features\n" +
                        "• You can disable location services, but this will prevent you from using ride services\n" +
                        "• Location history is retained to improve route optimization and service quality\n" +
                        "• Drivers can see passenger locations only during active rides\n" +
                        "• Location data may be used for safety investigations"
            )

            // Section 9
            SectionTitle("9. Children's Privacy")
            SectionContent(
                "• Our service is available to users 12 years and older\n" +
                        "• We do not knowingly collect data from children under 12\n" +
                        "• If we discover we have collected data from a child under 12, we will delete it promptly"
//                "• Users under 18 should use our service with parental consent and supervision\n"
            )

            // Section 10
            SectionTitle("10. Cookies and Tracking Technologies")
            SectionContent(
                "• We use cookies and similar technologies to enhance user experience\n" +
                        "• These help us remember your preferences and analyze app usage\n" +
                        "• You can control cookie settings through your device preferences\n" +
                        "• Some features may not function properly if cookies are disabled"
            )

            // Section 11
            SectionTitle("11. Third-Party Services")
            SectionContent(
                "Our app may integrate with third-party services:\n\n" +
                        "• Payment processors for transaction handling\n" +
                        "• Mapping services for navigation\n" +
                        "• Cloud storage providers\n" +
                        "• Analytics platforms\n\n" +
                        "These third parties have their own privacy policies. We recommend reviewing them."
            )

            // Section 12
            SectionTitle("12. Data Retention")
            SectionContent(
                "We retain your information for:\n\n" +
                        "• Active accounts: Duration of account existence\n" +
                        "• Transaction records: Minimum 5 years (as required by law)\n" +
                        "• ID documents: Duration of account existence plus 2 years\n" +
                        "• Deleted accounts: Retained for legal purposes (typically 30-90 days) then permanently deleted\n" +
                        "• Communications: Up to 3 years for customer service purposes"
            )

            // Section 13
            SectionTitle("13. International Data Transfers")
            SectionContent(
                "• Your data is primarily stored and processed in the Philippines\n" +
                        "• Some service providers may be located outside the Philippines\n" +
                        "• We ensure adequate safeguards are in place for international transfers\n" +
                        "• We comply with Data Privacy Act requirements for cross-border data transfer"
            )

            // Section 14
            SectionTitle("14. Changes to This Privacy Policy")
            SectionContent(
                "• We may update this Privacy Policy from time to time\n" +
                        "• Significant changes will be notified through the app or email\n" +
                        "• Updated policy will show the \"Last Updated\" date\n" +
                        "• Continued use after changes indicates acceptance of the new policy"
            )

            // Section 15
            SectionTitle("15. Data Breach Notification")
            SectionContent(
                "• In the event of a data breach, we will:\n" +
                        "  - Notify affected users within 72 hours\n" +
                        "  - Report to the National Privacy Commission as required\n" +
                        "  - Take immediate steps to secure data and prevent further breaches\n" +
                        "  - Provide guidance on protective measures users should take"
            )

            // Section 16
            SectionTitle("16. Contact Information")
            SectionContent(
                "For privacy-related questions or to exercise your data rights:\n\n" +
//                        "Data Protection Officer\n" +
                        "IslaMove Administrator\n" +
                        "Email: islamoveapp@gmail.com\n" +
//                        "Phone: 09089848952\n" +
                        "Address: Sta. Cruz\n\n" +
                        "National Privacy Commission:\n" +
                        "Website: www.privacy.gov.ph\n" +
                        "Email: info@privacy.gov.ph\n" +
                        "Hotline: (02) 8234-2228"
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "By clicking \"I agree to the Privacy Policy\" during registration, you acknowledge that you " +
                        "have read and understood how we collect, use, and protect your personal information.",
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