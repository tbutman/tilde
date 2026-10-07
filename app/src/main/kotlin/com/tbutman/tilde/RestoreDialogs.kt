package com.tbutman.tilde

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Restoring a backup file the owner picked: from Settings → Backup and restore, or from the
 * welcome's "Restore a backup" when moving to a new phone. Says what's wrong with a file that can't
 * be restored; otherwise shows what's in it, and replaces everything once confirmed.
 */
object RestoreDialogs {
    fun restore(activity: Activity, prefs: Prefs, uri: Uri) {
        val text = runCatching { activity.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        val backup = try {
            text?.let { Backups.fromJson(it) }
        } catch (e: Backups.Invalid) {
            return problem(activity, when (e.reason) {
                Backups.Reason.NOT_A_BACKUP -> R.string.backup_not_a_backup
                Backups.Reason.TOO_NEW -> R.string.backup_too_new
                Backups.Reason.NO_CARDS -> R.string.backup_no_cards
                Backups.Reason.DAMAGED -> R.string.backup_damaged
            })
        } ?: return problem(activity, R.string.backup_unreadable)
        val date = DateUtils.formatDateTime(activity, backup.created, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
        val res = activity.resources
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.backup_confirm_title)
            .setMessage(activity.getString(
                R.string.backup_confirm, date,
                res.getQuantityString(R.plurals.backup_confirm_cards, backup.cards.size, backup.cards.size),
                res.getQuantityString(R.plurals.backup_confirm_met, backup.met.size, backup.met.size),
            ))
            .setPositiveButton(R.string.backup_restore) { _, _ ->
                // Restore writes the photos before changing anything, so a failure (a full disk) leaves things as they were.
                if (runCatching { prefs.restore(backup) }.isFailure) return@setPositiveButton problem(activity, R.string.backup_restore_failed)
                TildeApp.applyTheme(prefs.theme)
                restartApp(activity)
            }
            .setNegativeButton(R.string.met_cancel, null)
            .show()
    }

    private fun problem(activity: Activity, message: Int) {
        MaterialAlertDialogBuilder(activity).setMessage(message).setPositiveButton(R.string.done, null).show()
    }

    /** After restoring or deleting everything: start Tilde afresh, so every screen shows the new data. */
    fun restartApp(activity: Activity) {
        activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        activity.finish()
    }

    /** What the file picker offers: backups are JSON, but some apps label them otherwise. */
    val TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")
}
