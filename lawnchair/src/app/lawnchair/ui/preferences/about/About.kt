/*
 * Copyright 2022, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences.about

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.navigation.AboutLicenses
import com.android.launcher3.BuildConfig
import com.android.launcher3.R

@Composable
fun About(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    PreferenceLayoutLazyColumn(
        label = stringResource(id = R.string.about_label),
        modifier = modifier,
        backArrowVisible = !LocalIsExpandedScreen.current,
    ) {
        item {
            Spacer(Modifier.padding(top = 10.dp))
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_home_comp),
                    contentDescription = stringResource(R.string.nexus_launcher_name),
                    modifier = Modifier.size(80.dp).clip(CircleShape),
                )
            }
        }
        item { Spacer(modifier = Modifier.height(14.dp)) }
        item {
            Text(
                text = stringResource(R.string.nexus_launcher_name),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                text = stringResource(R.string.nexus_creator_line),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Spacer(modifier = Modifier.height(14.dp)) }
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            Text(
                text = "Nexus UI 1.0",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                text = "A personalised interface for a simpler, more accessible home screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item {
            PreferenceGroupHeading("Interface information")
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    NexusInfoRow("Interface", "Nexus UI")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NexusInfoRow("UI version", "1.0")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NexusInfoRow("Launcher", "Nexus Launcher")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NexusInfoRow("Experience", "Nexus Fusion UI")
                }
            }
        }
        item { Spacer(modifier = Modifier.height(18.dp)) }
        item {
            PreferenceGroupHeading("About Nexus Launcher")
        }
        item {
            Text(
                text = "Nexus Launcher brings together a flexible home screen, customisable gestures, thoughtful privacy tools and an interface designed to be clear and comfortable to use.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Text(
                text = "Nexus Fusion UI began as an experiment in creating a custom launcher experience and grew into a consistent visual language for Nexus Launcher.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Text(
                text = "Created by Kuldeep Kumar Yadav",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item { Spacer(modifier = Modifier.height(18.dp)) }
        item {
            PreferenceGroupHeading(stringResource(R.string.nexus_contact_heading))
        }
        item {
            Text(
                text = stringResource(R.string.nexus_contact_email),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Text(
                text = stringResource(R.string.nexus_social_heading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                NexusSocialLink(context, "Instagram", "https://www.instagram.com/_kuldeep_kumar_yadav?obrf=bG83bDFlazllYXZn")
                NexusSocialLink(context, "X", "https://x.com/kuldeep8577")
                NexusSocialLink(context, "LinkedIn", "https://www.linkedin.com/in/kuldeep-kumar-yadav-36042421b?utm_source=share_via&utm_content=profile&utm_medium=member_android")
            }
        }
        item { Spacer(modifier = Modifier.height(18.dp)) }
        item {
            PreferenceGroupHeading(stringResource(R.string.nexus_legal_heading))
        }
        item {
            NavigationActionPreference(
                label = stringResource(R.string.nexus_open_source_licenses),
                destination = AboutLicenses,
            )
        }
        item {
            Text(
                text = stringResource(R.string.nexus_privacy_policy_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Text(
                text = stringResource(R.string.nexus_terms_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Text(
                text = stringResource(R.string.nexus_personal_use_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun NexusInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun NexusSocialLink(
    context: android.content.Context,
    label: String,
    url: String,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                },
                onLongClick = {},
            )
            .padding(vertical = 8.dp),
    )
}

