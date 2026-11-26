package com.example.liftcard

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

object Proto {
    // F0 4C 49 46 54 43 41 52 44 00  ("F0" + "LIFTCARD" + 00)
    val AID = byteArrayOf(0xF0.toByte(), 0x4C, 0x49, 0x46, 0x54, 0x43, 0x41, 0x52, 0x44, 0x00)
    val OK = byteArrayOf(0x90.toByte(), 0x00)
    val NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
}

/** Runs on the CARD phone: answers the reader's SELECT with this card's ID. */
class CardService : HostApduService() {
    override fun processCommandApdu(apdu: ByteArray, extras: Bundle?): ByteArray {
        val isSelect = apdu.size >= 5 + Proto.AID.size &&
            apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
            apdu.copyOfRange(5, 5 + Proto.AID.size).contentEquals(Proto.AID)
        if (!isSelect) return Proto.NOT_FOUND
        val id = getSharedPreferences("card", MODE_PRIVATE).getString("id", "LC-0001") ?: "LC-0001"
        return id.toByteArray(Charsets.UTF_8) + Proto.OK
    }

    override fun onDeactivated(reason: Int) {}
}
