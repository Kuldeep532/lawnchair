package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

/**
 * Nexus Launcher Secure Vault entry point.
 *
 * The storage/encryption implementation is intentionally kept separate from the settings
 * surface so the feature can evolve without making the launcher dashboard harder to navigate.
 */
@Composable
fun SecureVaultPreference(
    modifier: Modifier = Modifier,
) {
    PreferenceLayout(
        label = stringResource(R.string.secure_vault_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup {
            ClickablePreference(
                label = stringResource(R.string.secure_vault_label),
                subtitle = stringResource(R.string.secure_vault_description),
                onClick = {
                    // Secure Vault UI/workflow will be connected in the next implementation step.
                },
            )
        }
    }
}