package de.oejendorferdamm.dammboard.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Verschlüsselt das IServ-Passwort mit einem Schlüssel aus dem Android-Keystore (AES-GCM). Der
 * Schlüssel verlässt das Gerät nie; eine kopierte Einstellungsdatei nützt ohne ihn nichts.
 *
 * Bewusst fehlertolerant: Manche ältere Boards haben einen fehlerhaften Keystore. Klappt das
 * Verschlüsseln nicht, wird wie bisher im Klartext gespeichert, statt das Passwort zu verlieren.
 * Klappt das Entschlüsseln nicht mehr (z. B. nach Zurücksetzen des Keystores), kommt ein leeres
 * Passwort zurück – die App fordert dann einfach zur erneuten Eingabe auf.
 */
internal object Tresor {
    private const val SCHLUESSEL_NAME = "dammboard_iserv"
    private const val PRAEFIX = "enc1:"
    private const val IV_LAENGE = 12
    private const val TAG_BITS = 128

    private fun schluessel(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(SCHLUESSEL_NAME, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(SCHLUESSEL_NAME, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun verschluesseln(klartext: String): String {
        if (klartext.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, schluessel())
            val daten = cipher.doFinal(klartext.toByteArray(Charsets.UTF_8))
            PRAEFIX + Base64.encodeToString(cipher.iv + daten, Base64.NO_WRAP)
        } catch (e: Exception) {
            klartext
        }
    }

    fun entschluesseln(gespeichert: String): String {
        if (!gespeichert.startsWith(PRAEFIX)) return gespeichert // alter Klartext-Eintrag
        return try {
            val roh = Base64.decode(gespeichert.removePrefix(PRAEFIX), Base64.NO_WRAP)
            if (roh.size <= IV_LAENGE) return ""
            val iv = roh.copyOfRange(0, IV_LAENGE)
            val daten = roh.copyOfRange(IV_LAENGE, roh.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, schluessel(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(daten), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
