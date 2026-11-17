package com.tamerslab.vpet

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/**
 * Hospeda `assets/vpet/reader.html` + `lector.js` (recuperados do DBT-Link) numa WebView e faz
 * a ponte com o transporte Kotlin. Equivale ao par VpetActivity/Puente do DBT-Link, reduzido ao
 * necessário para leitura.
 *
 * Uso típico no Compose: `AndroidView(factory = { VpetReaderWebView(it, ...) })` e, nas telas,
 * conectar um [VpetTransport] chamando [onTransportLine] quando chegar linha.
 */
@SuppressLint("SetJavaScriptEnabled")
class VpetReaderWebView(
    context: Context,
    private val onSend: (String) -> Unit,            // JS→adaptador (Android.enviar)
    private val onProgress: (Float) -> Unit,
    private val onError: (String) -> Unit,
    private val onResult: (VpetReadResult) -> Unit,
) : WebView(context) {

    private val main = Handler(Looper.getMainLooper())

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true // lector.js guarda a versão aprendida em localStorage
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webViewClient = WebViewClient()
        addJavascriptInterface(Bridge(), "Android")
        loadUrl("file:///android_asset/vpet/reader.html")
    }

    /** Inicia a leitura de um tipo de VPet: dm20 | dmx | pen20 | penz | dmc | penc. */
    fun comecar(tipo: String) {
        evaluateJs("window.vpetComecar(${JSONObject.quote(tipo)})")
    }

    fun parar() {
        evaluateJs("window.vpetParar()")
    }

    /** Entrega uma linha recebida do transporte — mesmo papel de VpetActivity#linea. */
    fun onTransportLine(line: String) {
        evaluateJs("window.vpetLinea(${JSONObject.quote(line)})")
    }

    /** Status de conexão — mesmo papel de VpetActivity#estado. */
    fun onTransportStatus(ok: Boolean, msg: String) {
        val json = JSONObject().put("ok", ok).put("msg", msg)
        evaluateJs("window.vpetEstado($json)")
    }

    private fun evaluateJs(js: String) {
        main.post { evaluateJavascript(js, null) }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun enviar(texto: String) = onSend(texto)

        @JavascriptInterface
        fun progreso(p: Int) = onProgress(p.toFloat())

        @JavascriptInterface
        fun falla(mensagem: String) = onError(mensagem)

        @JavascriptInterface
        fun listo(json: String) {
            val d = JSONObject(json)
            onResult(
                VpetReadResult(
                    tipo = d.optString("tipo"),
                    version = d.optInt("version", 0),
                    indice = d.optInt("indice", 0),
                    nombre = d.optString("nombre").takeIf { it.isNotBlank() && it != "null" },
                    etapa = if (d.has("etapa") && !d.isNull("etapa")) d.optInt("etapa") else null,
                    attr = if (d.has("attr") && !d.isNull("attr")) d.optInt("attr") else null,
                    poder = if (d.has("poder") && !d.isNull("poder")) d.optInt("poder") else null,
                    hex = d.optString("hex"),
                    valido = d.optBoolean("valido", false),
                )
            )
        }

        @JavascriptInterface
        fun cerrar() { /* fechado pela tela */ }
    }
}
