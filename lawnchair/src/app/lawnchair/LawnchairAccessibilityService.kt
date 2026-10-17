/*
 * Copyright 2021, Lawnchair
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

package app.lawnchair

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import app.lawnchair.wellbeing.DigitalWellbeingBlockedActivity
import app.lawnchair.wellbeing.DigitalWellbeingController

class LawnchairAccessibilityService : AccessibilityService() {
    private var lastBlockedPackage: String? = null
    private var lastBlockedAt: Long = 0L

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            // Set the type of events that this service wants to listen to.  Others
            // won't be passed to this service.
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

            // If you only want this service to work with specific applications, set their
            // package names here.  Otherwise, when the service is activated, it will listen
            // to events from all applications.
            packageNames = emptyArray()
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        lawnchairApp.accessibilityService = this
    }

    override fun onDestroy() {
        lawnchairApp.accessibilityService = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        val controller = DigitalWellbeingController(this)
        if (!controller.shouldBlockPackage(packageName)) return

        val now = System.currentTimeMillis()
        if (lastBlockedPackage == packageName && now - lastBlockedAt < 5_000L) return
        lastBlockedPackage = packageName
        lastBlockedAt = now
        runCatching {
            startActivity(
                Intent(this, DigitalWellbeingBlockedActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }
}
