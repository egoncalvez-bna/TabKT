package com.kt.webconteinerapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ar.com.bna.security.crypto.CryptoProvider
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory


class MainActivity : AppCompatActivity() {
    private lateinit var myWebView: WebView
    private lateinit var progressBar: ProgressBar
    private val CAMERA_PERMISSION_REQUEST_CODE = 100

    @SuppressLint("SetJavaScriptEnabled")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        supportActionBar?.hide()

        // Verificar si el permiso ya ha sido concedido
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Si el permiso no está concedido, solicitarlo
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }


        // Obtener referencia a SharedPreferences
        val preferences = getSharedPreferences("mis_preferencias", Context.MODE_PRIVATE)
        val nombreEquipo = preferences.getString("nombreEquipo", "")
        val nombreServidor = preferences.getString("nombreServidor", "")
        val ambiente = preferences.getString("ambiente", "")
        val numeroSucursal = preferences.getString("numeroSucursal", "")
        Log.e("DeviceInfo", "Valor guardado: $nombreEquipo")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        myWebView = findViewById(R.id.webPagina)
        progressBar = findViewById(R.id.progressBar)

        myWebView.settings.javaScriptEnabled = true
        myWebView.webViewClient = MyWebViewClient(progressBar)
        myWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = ProgressBar.VISIBLE
                } else {
                    progressBar.visibility = ProgressBar.GONE
                }
                progressBar.progress = newProgress
                Log.d("WebViewLog", "Loading progress: $newProgress")
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }
        }
        // Configurar el certificado SSL
        configureSsl(myWebView)

        var pass = BuildConfig.PASSWORD
        Log.e("PasswordContent", "La contraseña es: $pass")
        var user = BuildConfig.DOMAIN_USER + "\\" + BuildConfig.USERNAME
        Log.e("PasswordContent", "$user")
        var p = BuildConfig.DOMAIN_SERVER_SUC
        Log.e("PasswordContent", "$p")

        if (pass.isNullOrEmpty()) {
            // Manejar el caso en el que la contraseña es nula o vacía
            Log.e("PasswordError", "La contraseña no está definida en BuildConfig.")
            sendError("La contraseña no está definida en BuildConfig.")
        }

        myWebView.setWebViewClient(object : MyWebViewClient(progressBar) {
            override fun onReceivedHttpAuthRequest(
                view: WebView,
                handler: HttpAuthHandler,
                host: String,
                realm: String
            ) {
                var username =
                    BuildConfig.DOMAIN_USER + "\\" + BuildConfig.USERNAME + numeroSucursal

                if (ambiente == "DSUC" && numeroSucursal == "0085") {
                    username =
                        BuildConfig.DOMAIN_USER + "\\" + BuildConfig.USERNAME + "0074"
                }
                var password = ""
                try {
                    val crypto = CryptoProvider.getProvider().simCrypto
                    password = crypto.desencriptar(pass)
                } catch (e: Exception) {
                    Log.e("BNA-Crypto", "Error al utilizar BNA-Crypto", e)
                }
                handler.proceed(username, password)
            }
        })
        val versionName = BuildConfig.VERSION_NAME
        val url =
            "https://$nombreServidor/BNA.KT.Totem.Tab/Default.aspx?nombreEquipo=$nombreEquipo&apkVersion=V$versionName"
        myWebView.loadUrl(url)

        // Desactiva el movimiento en los márgenes
        myWebView.setOnTouchListener { _, event -> event.actionMasked == MotionEvent.ACTION_MOVE }
    }


    fun sendError(error: String) {
        val intent = Intent(this, ErroresActivity::class.java)
        intent.putExtra("EXTRA_TEXT", error)
        startActivity(intent)
        return
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun configureSsl(webView: WebView) {
        try {
            // Crear un CertificateFactory para X.509
            val cf = CertificateFactory.getInstance("X.509")

            // Lista de certificados a cargar
            val certificates = listOf(
                R.raw.cabnaprodroot,
                R.raw.cabnaprod01,
                R.raw.cabnaprod02,
                R.raw.cabnatestroot,
                R.raw.cabnatest01,
                R.raw.cabnatest02
            )

            // Crear un KeyStore que contiene los CAs confiables
            val keyStoreType = KeyStore.getDefaultType()
            val keyStore = KeyStore.getInstance(keyStoreType).apply {
                load(null, null)
            }

            // Cargar cada certificado y añadirlo al KeyStore
            certificates.forEachIndexed { index, certResId ->
                resources.openRawResource(certResId).use { caInput ->
                    val ca = cf.generateCertificate(caInput)
                    keyStore.setCertificateEntry("ca$index", ca)
                }
            }

            // Crear un TrustManager que confía en los CAs en nuestro KeyStore
            val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
            val tmf = TrustManagerFactory.getInstance(tmfAlgorithm).apply {
                init(keyStore)
            }

            // Crear un SSLContext que usa nuestro TrustManager
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, tmf.trustManagers, null)
            }

            val sslSocketFactory = sslContext.socketFactory
            (webView.webViewClient as MyWebViewClient).sslSocketFactory = sslSocketFactory
        } catch (e: Exception) {
            Log.e("SSLConfig", "Error configuring SSL", e)
        }
    }


    private fun hideSystemUI() {
        val decorView = window.decorView
        decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }

    open inner class MyWebViewClient(private val progressBar: ProgressBar) : WebViewClient() {
        var sslSocketFactory: SSLSocketFactory? = null

        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            Log.d("WebViewClient", "Loading URL: $url")
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            Log.d("WebViewClient", "onPageStarted")
            progressBar.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Log.d("WebViewClient", "onPageFinished")
            progressBar.visibility = View.GONE
        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            error?.let {
                val errorCode = it.errorCode
                val description = it.description.toString()
                Log.e("WebViewError", "Error $errorCode: $description")

                sendError("WV Error: $errorCode: $description")

                if (description.contains("net::ERR_CONNECTION_TIMED_OUT")) {
                    Log.e("WebViewError", "Error de tiempo de conexión agotado.")

                }
            }
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?
        ) {
            handler?.cancel()

            Log.e("WebViewClientError", "SSL Error: ${error?.primaryError}")
            sendError("SSL Error: ${error?.primaryError}")
        }
    }
}
