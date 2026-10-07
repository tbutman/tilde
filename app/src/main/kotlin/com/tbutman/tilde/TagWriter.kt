package com.tbutman.tilde

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef as NdefTech
import android.nfc.tech.NdefFormatable
import java.io.IOException

/**
 * Writes an NDEF message to a tag held to the phone: an NFC sticker, a printed card with one inside,
 * or a blank tag that still needs formatting. Locks it (read-only, for good) only when asked to;
 * otherwise it can always be written again.
 */
object TagWriter {
    sealed interface Result {
        data object Written : Result
        /** Written and made read-only, as asked: nobody can change it now. */
        data object WrittenLocked : Result
        /** Written, but this tag can't be made read-only. */
        data object WrittenNotLockable : Result
        /** Read-only: a locked tag, or a phone answering as a tag. */
        data object Locked : Result
        data class TooSmall(val needed: Int, val capacity: Int) : Result
        /** Not a tag that holds NDEF, such as a bank card or a transit card. */
        data object Unsupported : Result
        /** Moved away too soon, or the tag didn't answer. */
        data object Failed : Result
    }

    /** What writing `size` bytes would do to an NDEF tag, before touching it. */
    fun check(size: Int, capacity: Int, writable: Boolean): Result? = when {
        !writable -> Result.Locked
        size > capacity -> Result.TooSmall(size, capacity)
        else -> null
    }

    /** After a successful write: what locking did, when it was asked for. */
    fun locked(lockAsked: Boolean, lockWorked: Boolean): Result = when {
        !lockAsked -> Result.Written
        lockWorked -> Result.WrittenLocked
        else -> Result.WrittenNotLockable
    }

    /** Whether a write went through (locked or not). */
    fun wrote(result: Result?) = result == Result.Written || result == Result.WrittenLocked || result == Result.WrittenNotLockable

    /** Called on the reader thread. `lock` makes the tag read-only afterwards, permanently. */
    fun write(tag: Tag, message: ByteArray, lock: Boolean = false): Result {
        val ndefMessage = try {
            NdefMessage(message)
        } catch (_: FormatException) {
            return Result.Failed
        }
        return try {
            NdefTech.get(tag)?.use { ndef ->
                ndef.connect()
                check(ndefMessage.byteArrayLength, ndef.maxSize, ndef.isWritable)?.let { return it }
                ndef.writeNdefMessage(ndefMessage)
                locked(lock, lock && ndef.canMakeReadOnly() && ndef.makeReadOnly())
            } ?: NdefFormatable.get(tag)?.use { blank ->
                blank.connect()
                if (lock) blank.formatReadOnly(ndefMessage) else blank.format(ndefMessage)
                locked(lock, lock)
            } ?: Result.Unsupported
        } catch (_: IOException) {
            Result.Failed
        } catch (_: FormatException) {
            Result.Failed
        } catch (_: SecurityException) {
            // The tag left the field and Android invalidated it.
            Result.Failed
        }
    }
}
