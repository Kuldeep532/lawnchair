package app.lawnchair.ui.preferences.legal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme

private data class LegalSection(val heading: String, val body: String)

class LegalDocumentsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val document = intent.getStringExtra(EXTRA_DOCUMENT) ?: PRIVACY
        val isTerms = document == TERMS
        val title = if (isTerms) "Terms & Conditions" else "Privacy Policy"
        val sections = if (isTerms) termsSections else privacySections

        setContent {
            LawnchairTheme {
                EdgeToEdge()
                Scaffold(
                    topBar = { TopAppBar(title = { Text(title) }) },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Nexus Launcher · Effective 17 October 2026", style = MaterialTheme.typography.bodySmall)
                        sections.forEach { section ->
                            Text(section.heading, style = MaterialTheme.typography.titleMedium)
                            Text(section.body, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_DOCUMENT = "app.lawnchair.legal.DOCUMENT"
        const val PRIVACY = "privacy"
        const val TERMS = "terms"
    }
}

private val privacySections = listOf(
    LegalSection("1. Overview", "Nexus Launcher is a personal Android launcher project. This policy explains what information may be processed by launcher features and how Android permissions affect that processing."),
    LegalSection("2. Information processed on your device", "The launcher processes settings, home-screen preferences, selected apps and other configuration needed to provide its features. App names and launchable activities may be read to display the app drawer and search results. This information is generally processed on your device."),
    LegalSection("3. Digital Wellbeing and Accessibility", "If you choose to enable Usage Access, the Digital Wellbeing feature reads Android usage statistics to calculate app usage limits. If you explicitly enable the Nexus Launcher Accessibility service, it observes app-window changes so selected distracting apps can be interrupted during a focus session. These permissions are optional for the launcher overall and should be enabled only if you want the relevant feature. You can revoke them in Android Settings."),
    LegalSection("4. Other permissions", "Features may request access to media, audio, contacts, biometrics or network connectivity when needed. Android permission prompts and system settings control these grants. Do not grant a permission unless you understand why the feature needs it."),
    LegalSection("5. Accounts and online services", "If an optional account, cloud, AI, voice, search or other online integration is enabled, the information you submit may be sent to the selected provider to fulfil that request. The provider's own privacy terms apply to its processing. Do not submit passwords, API keys or sensitive personal information unless the relevant feature explicitly requires it and you trust the provider."),
    LegalSection("6. Third-party links and services", "The launcher may open third-party websites, apps, media providers or learning resources. Those services are independent and may collect information under their own policies. Nexus Launcher does not control their practices."),
    LegalSection("7. Sharing and advertising", "This project does not intentionally sell personal information. Data may be processed by a third-party service when you choose to use an integration, or when disclosure is required by applicable law. This statement does not override the privacy terms of services you independently enable."),
    LegalSection("8. Retention and security", "Local preferences remain on your device until you clear app data or uninstall the launcher. Online services may retain data according to their own settings and policies. Keep your device updated, use a secure screen lock and avoid sharing diagnostic logs that contain personal information."),
    LegalSection("9. Children and sensitive data", "The launcher is a general-purpose utility and is not designed to collect children's personal information. Avoid storing or entering sensitive data into features that are not specifically designed for it."),
    LegalSection("10. Your choices", "You can review or revoke Android permissions in Settings, disable optional features, and stop using integrations. Some features will not work without their related permissions."),
    LegalSection("11. Changes and contact", "This policy may change as the project evolves. The effective date above identifies this version. For questions or corrections, use the issue tracker at github.com/Kuldeep532/lawnchair. Do not post private information in public issues."),
)

private val termsSections = listOf(
    LegalSection("1. Acceptance", "By installing or using Nexus Launcher, you agree to these terms. If you do not agree, stop using the launcher and uninstall it."),
    LegalSection("2. Personal and open-source project", "Nexus Launcher is a personal customization of the Lawnchair/Launcher3 codebase. Upstream components and third-party libraries remain subject to their own licences and notices. These terms do not replace or restrict rights granted by applicable open-source licences."),
    LegalSection("3. Appropriate use", "Use the launcher lawfully and do not use it to interfere with another person's device, accounts, privacy or security. You are responsible for the permissions you grant and the content or credentials you enter into connected services."),
    LegalSection("4. Permissions and device control", "Some features require optional Android permissions, including Usage Access or Accessibility. These features must be enabled by the device user through Android settings. Focus Mode is a best-effort aid, not a security boundary; it cannot guarantee that another app will remain blocked, prevent settings changes, or prevent launcher removal."),
    LegalSection("5. Third-party services", "External apps, websites, AI providers, search engines, voice services and media sources are operated independently. Their availability, content, costs and terms may change. You are responsible for reviewing their terms before use."),
    LegalSection("6. No professional advice", "Information and learning links are provided for convenience and education only. The launcher does not provide legal, medical, financial or other professional advice."),
    LegalSection("7. Availability and changes", "Features may be changed, suspended or removed as development continues. No particular feature, compatibility level or future update is guaranteed."),
    LegalSection("8. Disclaimer", "To the extent permitted by applicable law, the launcher is provided on an as-is and as-available basis without warranties of uninterrupted operation, error-free behaviour or fitness for a particular purpose. Keep backups of important data and verify critical actions independently."),
    LegalSection("9. Limitation of liability", "To the extent permitted by applicable law, the project contributors are not liable for indirect or consequential loss arising from use of the launcher, third-party services or optional permissions. Nothing in these terms excludes liability that cannot legally be excluded."),
    LegalSection("10. Updates to these terms", "These terms may be updated as the project changes. Continued use after an update means you accept the revised terms to the extent permitted by applicable law."),
    LegalSection("11. Contact", "For questions or corrections, use the issue tracker at github.com/Kuldeep532/lawnchair. Do not publish passwords, API keys, device identifiers or other private information in public issues."),
)
