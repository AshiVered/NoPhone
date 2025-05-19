package org.fossify.phone.services

import android.app.KeyguardManager
import android.content.Context
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
// import org.fossify.phone.activities.CallActivity // CallActivity is no longer started from here
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.isOutgoing
import org.fossify.phone.extensions.powerManager
import org.fossify.phone.extensions.getStateCompat
import org.fossify.phone.helpers.CallManager
import org.fossify.phone.helpers.CallNotificationManager
import org.fossify.phone.helpers.NoCall
import org.fossify.phone.models.Events
import org.greenrobot.eventbus.EventBus

class CallService : InCallService() {
    private val callNotificationManager by lazy { CallNotificationManager(this) }

    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                callNotificationManager.cancelNotification()
            } else {
                // Even if we reject/disconnect, the system might still expect a notification for a brief period.
                // Or we could decide to not show notifications at all for a background-only app.
                // For now, let's keep it, as CallActivity itself will finish immediately.
                callNotificationManager.setupNotification()
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.inCallService = this
        call.registerCallback(callListener)

        // --- MODIFICATION TO REJECT/DISCONNECT ANY CALL ---
        val callState = call.getStateCompat()
        if (callState == Call.STATE_RINGING) {
            call.reject(false, null)
        } else if (callState != Call.STATE_DISCONNECTED && callState != Call.STATE_DISCONNECTING) {
            call.disconnect()
        }
        // --- END MODIFICATION ---

        // The following logic tries to show UI or notifications.
        // Since we are aiming for a background-only app that rejects calls,
        // we might not want to start CallActivity or even show detailed notifications.
        // However, the system might still require some notification handling.
        // For now, we remove direct CallActivity starts.
        // The notification setup might still try to create an intent for CallActivity,
        // but CallActivity itself will be modified to finish immediately.

        val isScreenLocked = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
        if (!powerManager.isInteractive || call.isOutgoing() || isScreenLocked || config.alwaysShowFullscreen) {
            try {
                callNotificationManager.setupNotification(true) // High priority for system
                // startActivity(CallActivity.getStartIntent(this)) // REMOVED
            } catch (e: Exception) {
                callNotificationManager.setupNotification() // Fallback notification
            }
        } else {
            callNotificationManager.setupNotification()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callListener)
        // val wasPrimaryCall = call == CallManager.getPrimaryCall() // No longer needed if CallActivity isn't started
        CallManager.onCallRemoved(call)
        if (CallManager.getPhoneState() == NoCall) {
            CallManager.inCallService = null
            callNotificationManager.cancelNotification()
        } else {
            callNotificationManager.setupNotification()
            // if (wasPrimaryCall) { // REMOVED block
            //     startActivity(CallActivity.getStartIntent(this))
            // }
        }

        EventBus.getDefault().post(Events.RefreshCallLog)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallManager.onAudioStateChanged(audioState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        callNotificationManager.cancelNotification()
    }
}
