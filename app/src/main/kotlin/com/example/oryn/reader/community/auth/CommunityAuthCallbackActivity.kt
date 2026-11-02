package com.example.oryn.reader.community.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.example.oryn.reader.MainActivity

/** Auth links never pass through MainActivity's file-import intent handler. */
class CommunityAuthCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(incoming: Intent?) {
        val callback = if (incoming?.action == Intent.ACTION_VIEW) incoming.dataString else null
        incoming?.data = null
        intent = Intent() // Never retain callback codes in instance state, recents or forwarded intents.
        if (callback != null && AuthConfiguration.fromBuild().isEnabled) {
            CommunityAuthRuntime.get(applicationContext).receiveCallback(callback)
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        finish()
    }
}
