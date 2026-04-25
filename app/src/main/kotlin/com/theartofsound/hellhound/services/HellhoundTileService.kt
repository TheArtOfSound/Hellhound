package com.theartofsound.hellhound.services

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.theartofsound.hellhound.MainActivity

/**
 * Quick Settings tile: pull down twice from the top of the screen, tap
 * the Hellhound tile, and you're in the chat. Useful for launching the
 * assistant without hunting for the icon.
 *
 * Requires the user to drag the tile into the active Quick Settings
 * row once (Android shows it in the "Edit" tray automatically).
 */
class HellhoundTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Hellhound"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapseCompat(launch)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launch)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun startActivityAndCollapseCompat(intent: Intent) {
        val pi = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        startActivityAndCollapse(pi)
    }
}
