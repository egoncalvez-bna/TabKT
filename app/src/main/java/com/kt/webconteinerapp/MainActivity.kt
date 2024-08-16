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

        // Prueba de implementación de BNA cripto:
        /*   val pass = when (ambiente) {
               "Producción" -> ""
               "Testing" -> "+GFwS5Wg1Q54A1kyEONCpg=="
               "Desarrollo" -> "ep+OHJBHisF4A1kyEONCpg=="

               else -> {
                   "ERROR"
               }
           }
           {}*/

        var pass = BuildConfig.PASSWORD
        Log.e("PasswordContent", "La contraseña es: $pass")

        if (pass.isNullOrEmpty()) {
            // Manejar el caso en el que la contraseña es nula o vacía
            Log.e("PasswordError", "La contraseña no está definida en BuildConfig.")
            val intent = Intent(this, ErroresActivity::class.java)
            intent.putExtra("EXTRA_TEXT", "Error: la contraseña es nula o vacía")
            startActivity(intent)
            return // Detener la ejecución si la contraseña no es válida
        }

        myWebView.setWebViewClient(object : MyWebViewClient(progressBar) {
            override fun onReceivedHttpAuthRequest(
                view: WebView,
                handler: HttpAuthHandler,
                host: String,
                realm: String
            ) {
                val username = when (ambiente) {
                    "Producción" -> "CC\\H00097"
                    "Testing" -> "TCC\\H00097"
                    "Desarrollo" -> "DCC\\H00097"

                    else -> {
                        "CC\\H00097"
                    }
                }
                var password =""
                try {
                    val crypto = CryptoProvider.getProvider().simCrypto
                    password = crypto.desencriptar(pass)
                } catch (e: Exception) {
                    Log.e("BNA-Crypto", "Error al utilizar BNA-Crypto", e)
                }
                handler.proceed(username, password)
            }
        })

        val url = "https://$nombreServidor/BNA.KT.Totem.Tab/Default.aspx?nombreEquipo=$nombreEquipo"
        myWebView.loadUrl(url)

        // Desactiva el movimiento en los márgenes
        myWebView.setOnTouchListener { _, event -> event.actionMasked == MotionEvent.ACTION_MOVE }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun configureSslOld(webView: WebView) {
        try {
            // Cargar el certificado desde assets
            val cf = CertificateFactory.getInstance("X.509")
//            val caInput = resources.openRawResource(R.raw.dap13cc0001)
            val caInput = resources.openRawResource(R.raw.dmf01sc6220)
            val ca = caInput.use {
                cf.generateCertificate(it)
            }

            // Crear un KeyStore que contiene el CA confiable
            val keyStoreType = KeyStore.getDefaultType()
            val keyStore = KeyStore.getInstance(keyStoreType).apply {
                load(null, null)
                setCertificateEntry("ca", ca)
            }

            // Crear un TrustManager que confía en el CA en nuestro KeyStore
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun configureSsl(webView: WebView) {
        try {
            // Crear un CertificateFactory para X.509
            val cf = CertificateFactory.getInstance("X.509")

            // Lista de certificados a cargar
            val certificates = listOf(

                R.raw.cabnaroottest,
                R.raw.certrootintertest,
                R.raw.cabnaroot
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

                // Crear Intent para iniciar SecondActivity
                val intent = Intent(this@MainActivity, ErroresActivity::class.java)
                // Agregar el texto como un
                intent.putExtra("EXTRA_TEXT", "Error $errorCode: $description")
                // Iniciar SecondActivity
                startActivity(intent)

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
            // Crear Intent para iniciar SecondActivity
            val intent = Intent(this@MainActivity, ErroresActivity::class.java)
            // Agregar el texto como un
            intent.putExtra("EXTRA_TEXT", "SSL Error: ${error?.primaryError}")
            // Iniciar SecondActivity
            startActivity(intent)
        }
//
    }
}
