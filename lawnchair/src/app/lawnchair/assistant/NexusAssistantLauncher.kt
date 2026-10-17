package app.lawnchair.assistant

import android.content.Context
import android.content.Intent

/**
 * Entry point for launcher surfaces, gestures, and Tools Studio.
 *
 * Example:
 * context.startActivity(NexusAssistantLauncher.intent(context))
 */
object NexusAssistantLauncher {
    fun intent(context: Context): Intent =
        Intent(context, NexusAssistantActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun modelsIntent(context: Context): Intent =
        intent(context).putExtra(NexusAssistantActivity.EXTRA_OPEN_MODELS, true)
}
