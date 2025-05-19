package org.fossify.phone.activities

import android.content.Intent // Keep this import if needed by BaseSplashActivity, though not used directly here anymore
import org.fossify.commons.activities.BaseSplashActivity

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        // startActivity(Intent(this, MainActivity::class.java)) // Remove or comment out this line
        finish() // Add this line to finish the activity immediately
    }
}
