package com.example.liftcard

import android.app.Activity
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity(), NfcAdapter.ReaderCallback {
    // The READER decides who may go where (like a real lift controller).
    private val allowed = mapOf(
        "LC-0001" to Pair("B 23-03A", setOf(23)),
        "LC-0002" to Pair("Visitor", setOf(5))
    )
    private val requestedFloor = 23

    private var nfc: NfcAdapter? = null
    private var readerOn = false
    private lateinit var status: TextView
    private lateinit var idInput: EditText

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        nfc = NfcAdapter.getDefaultAdapter(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        root.addView(TextView(this).apply { text = "Lift Card (school project demo)"; textSize = 20f })
        root.addView(TextView(this).apply { text = "Card ID on this phone:" })
        idInput = EditText(this).apply {
            setText(getSharedPreferences("card", MODE_PRIVATE).getString("id", "LC-0001"))
        }
        root.addView(idInput)
        root.addView(Button(this).apply { text = "USE AS CARD"; setOnClickListener { cardMode() } })
        root.addView(Button(this).apply { text = "USE AS READER"; setOnClickListener { readerMode() } })
        status = TextView(this).apply {
            textSize = 22f; gravity = Gravity.CENTER; setPadding(pad, pad * 2, pad, pad * 2)
            text = "Pick a mode"
        }
        root.addView(status)
        setContentView(root)
    }

    private fun show(msg: String, color: Int) { status.text = msg; status.setBackgroundColor(color); status.setTextColor(if (color == Color.TRANSPARENT) Color.BLACK else Color.WHITE) }

    private fun cardMode() {
        readerOn = false
        nfc?.disableReaderMode(this)
        val id = idInput.text.toString().trim().ifEmpty { "LC-0001" }
        getSharedPreferences("card", MODE_PRIVATE).edit().putString("id", id).apply()
        if (nfc?.isEnabled != true) { show("Turn NFC on in Settings", Color.GRAY); return }
        show("CARD MODE\nID: $id\nKeep screen on and tap the reader phone", Color.parseColor("#2563eb"))
    }

    private fun readerMode() {
        readerOn = true
        startReader()
    }

    private fun startReader() {
        val a = nfc
        if (a == null || !a.isEnabled) { show("NFC missing or off. Turn it on in Settings", Color.GRAY); return }
        a.enableReaderMode(this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
        show("READER MODE\nTap the card phone on the back of this phone", Color.TRANSPARENT)
    }

    override fun onResume() { super.onResume(); if (readerOn) startReader() }
    override fun onPause() { super.onPause(); nfc?.disableReaderMode(this) }

    override fun onTagDiscovered(tag: Tag) {
        val iso = IsoDep.get(tag) ?: return
        try {
            iso.connect()
            val select = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, Proto.AID.size.toByte()) + Proto.AID + byteArrayOf(0x00)
            val r = iso.transceive(select)
            val ok = r.size >= 2 && r[r.size - 2] == 0x90.toByte() && r[r.size - 1] == 0x00.toByte()
            val id = if (ok) String(r, 0, r.size - 2, Charsets.UTF_8) else ""
            runOnUiThread { result(id) }
        } catch (e: Exception) {
            runOnUiThread { show("Read failed. Hold the phones together and try again", Color.GRAY) }
        } finally {
            try { iso.close() } catch (_: Exception) {}
        }
    }

    private fun result(id: String) {
        val e = allowed[id]
        if (e != null && requestedFloor in e.second)
            show("ACCESS GRANTED\n$id\n${e.first} · Floor $requestedFloor", Color.parseColor("#16a34a"))
        else
            show("ACCESS DENIED\n${if (id.isEmpty()) "Not a lift card" else id}", Color.parseColor("#dc2626"))
        status.postDelayed({ if (readerOn) show("READER MODE\nReady for next card", Color.TRANSPARENT) }, 5000)
    }
}
