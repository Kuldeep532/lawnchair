package app.lawnchair.ui.preferences.destinations

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.preferences.getAdapter
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

@Composable
fun ControlCenterPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager2()

    PreferenceLayout(
        label = stringResource(R.string.control_center_notifications_label),
        modifier = modifier,
    ) {
        PreferenceGroup(
            heading = stringResource(R.string.control_center_style_heading),
            description = stringResource(R.string.control_center_style_description),
        ) {
            SwitchPreference(
                adapter = prefs.controlCenterGlass.getAdapter(),
                label = stringResource(R.string.control_center_glass_label),
                description = stringResource(R.string.control_center_glass_description),
            )
            SwitchPreference(
                adapter = prefs.controlCenterLargeTiles.getAdapter(),
                label = stringResource(R.string.control_center_large_tiles_label),
                description = stringResource(R.string.control_center_large_tiles_description),
            )
        }

        PreferenceGroup(
            heading = stringResource(R.string.control_center_panels_heading),
        ) {
            SwitchPreference(
                adapter = prefs.controlCenterNotifications.getAdapter(),
                label = stringResource(R.string.control_center_notifications_toggle),
                description = stringResource(R.string.control_center_notifications_toggle_description),
            )
            SwitchPreference(
                adapter = prefs.controlCenterQuickSettings.getAdapter(),
                label = stringResource(R.string.control_center_quick_settings_toggle),
                description = stringResource(R.string.control_center_quick_settings_toggle_description),
            )
        }

        Text(
            text = stringResource(R.string.control_center_system_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
