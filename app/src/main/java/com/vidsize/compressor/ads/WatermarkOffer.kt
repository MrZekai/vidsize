package com.vidsize.compressor.ads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What a rewarded ad buys: one export without the Vidsize mark.
 *
 * ## Why this replaced the ten-minute ad-free window
 *
 * Up to v0.9.3 the reward was silence - ten minutes during which no banner, no
 * native, no interstitial and no app-open ad could be requested. That reward was
 * revenue-NEGATIVE: the app traded one rewarded impression for ten minutes of
 * empty inventory, and it asked the user to care about a cost they were not
 * feeling at the moment of the offer.
 *
 * A mark on the file they are about to send someone is a cost they can see. The
 * reward removes it, suppresses nothing, and recurs naturally - every export is
 * a fresh occasion to offer it, where a time window actively suppressed the
 * next ten minutes of them.
 *
 * ## Why a single-use grant and not a flag
 *
 * The grant is CONSUMED by the export it pays for. It cannot accumulate, it
 * does not survive the process, and it is not a setting the user can leave
 * switched on - one ad, one clean file. Anything else would be a subscription
 * the app has no way to honour or to end.
 *
 * Deliberately not persisted: a grant that outlived a restart would have to be
 * defended against clock and storage tampering, which is exactly the class of
 * problem the old wall-clock window created. Losing a grant to a process death
 * costs the user one ad view; keeping it costs the app a whole threat model.
 */
object WatermarkOffer {

    /**
     * True while an earned, unspent mark-free export is waiting to be taken.
     *
     * Compose state so the output chooser can recognise an earned grant and
     * proceed without charging the user a second ad after a failed export.
     */
    var granted: Boolean by mutableStateOf(false)
        private set

    /** Called only from the rewarded SDK's own reward callback. */
    fun grant() {
        granted = true
    }

    /**
     * Acknowledges that the clean export was delivered and spends the grant.
     *
     * It is intentionally called on successful completion, not when encoding
     * starts. A decoder, encoder or storage failure leaves the paid-for grant in
     * hand so the retry remains mark-free.
     */
    fun consume(): Boolean {
        if (!granted) return false
        granted = false
        return true
    }

    /** Drops an unspent grant, for a flow the user abandoned. */
    fun clear() {
        granted = false
    }
}
