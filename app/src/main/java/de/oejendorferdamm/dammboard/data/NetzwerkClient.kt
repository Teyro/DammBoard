package de.oejendorferdamm.dammboard.data

import okhttp3.OkHttpClient

/** Ein einziger geteilter OkHttp-Client für IServ und Update-Prüfung statt mehrerer eigener Instanzen. */
object NetzwerkClient {
    val instance: OkHttpClient by lazy { OkHttpClient() }
}
