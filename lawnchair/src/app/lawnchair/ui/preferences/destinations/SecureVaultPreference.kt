package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import app.lawnchair.secureworld.SecureWorldActivity
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

@Composable
fun SecureVaultPreference(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

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
                    context.startActivity(Intent(context, SecureWorldActivity::class.java))
                },
            )
        }
    }
}