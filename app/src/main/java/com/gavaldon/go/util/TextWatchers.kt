package com.gavaldon.go.util

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import com.gavaldon.go.domain.CardValidator

/** TextWatcher que solo implementa afterTextChanged. */
fun EditText.afterTextChanged(block: (Editable) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) = block(s)
    })
}

/** Da formato "1234 5678 ..." al número de tarjeta mientras se escribe. */
fun EditText.formatAsCardNumber() {
    var busy = false
    afterTextChanged { editable ->
        if (busy) return@afterTextChanged
        busy = true
        val digits = CardValidator.digitsOnly(editable).take(19)
        val formatted = CardValidator.groupForDisplay(digits)
        if (formatted != editable.toString()) {
            editable.replace(0, editable.length, formatted)
        }
        busy = false
    }
}

/** Da formato "MM/AA" mientras se escribe. */
fun EditText.formatAsExpiry() {
    var busy = false
    afterTextChanged { editable ->
        if (busy) return@afterTextChanged
        busy = true
        val digits = CardValidator.digitsOnly(editable).take(4)
        val formatted = if (digits.length > 2) digits.substring(0, 2) + "/" + digits.substring(2) else digits
        if (formatted != editable.toString()) {
            editable.replace(0, editable.length, formatted)
        }
        busy = false
    }
}

/** Copia el texto a un CharArray para poder borrarlo de memoria después de usarlo. */
fun Editable?.toCharArraySafe(): CharArray {
    if (this == null) return CharArray(0)
    val out = CharArray(length)
    getChars(0, length, out, 0)
    return out
}
