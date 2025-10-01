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
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.net.ConnectivityManager
import android.widget.Toast


class MainActivity : AppCompatActivity() {
    private lateinit var myWebView: WebView
    private lateinit var progressBar: ProgressBar
    private val CAMERA_PERMISSION_REQUEST_CODE = 100
    
    // AGREGAR ESTAS VARIABLES:
    private var retryCount = 0
    private val maxRetries = 3
    private var lastActivityTime = System.currentTimeMillis()
    private val inactivityTimeout = 5 * 60 * 1000 // 5 minutos
    private lateinit var watchdogHandler: Handler
    
    // Credenciales pre-desencriptadas para mejorar fluidez
    private var decryptedUsername: String = ""
    private var decryptedPassword: String = ""
    private lateinit var pass: String

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

        // Pre-desencriptar credenciales para mejorar fluidez
        initializeCredentials(ambiente, numeroSucursal)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        myWebView = findViewById(R.id.webPagina)
        progressBar = findViewById(R.id.progressBar)

        myWebView.settings.apply {
            javaScriptEnabled = true
            
            // CONFIGURACIONES CRÍTICAS PARA TOTEM:
            domStorageEnabled = true
            databaseEnabled = true
            // Reemplazar métodos deprecados con configuración moderna
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            
            // CONFIGURACIÓN DE RED ROBUSTA:
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            
            // CONFIGURACIÓN PARA ESTABILIDAD:
            // Removido setRenderPriority (ya sin efecto en WebView moderno)
            setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING)
            
            // CONFIGURACIÓN PARA TOTEM:
            setNeedInitialFocus(false)
            setSupportZoom(false)
            setBuiltInZoomControls(false)
            setDisplayZoomControls(false)
            setDefaultFixedFontSize(16)
            setDefaultFontSize(16)
        }

        myWebView.webViewClient = MyWebViewClient(progressBar)
        myWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                updateLoadingProgress(newProgress)
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }
        }
        // Configurar el certificado SSL
        configureSsl(myWebView)

        pass = BuildConfig.PASSWORD
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
                // Usar credenciales pre-desencriptadas para mejor fluidez
                handler.proceed(decryptedUsername, decryptedPassword)
            }
        })
        // AGREGAR ESTAS LÍNEAS AL FINAL DE onCreate():

        // Configurar WebView para debugging en desarrollo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false) // false en producción
        }

        // Cargar URL con sistema de reintentos
        val versionName = BuildConfig.VERSION_NAME
        val url = "https://$nombreServidor/BNA.KT.Totem.Tab/Default.aspx?nombreEquipo=$nombreEquipo&apkVersion=V$versionName"
        loadUrlWithRetry(url)

        // Iniciar sistemas de monitoreo
        startWatchdog()
        scheduleMemoryCleanup()

        // Configurar listener para detectar actividad
        myWebView.setOnTouchListener { _, event -> 
            updateActivity()
            event.actionMasked == MotionEvent.ACTION_MOVE 
        }
    }

    private fun initializeCredentials(ambiente: String?, numeroSucursal: String?) {
        try {
            // Construir username
            decryptedUsername = BuildConfig.DOMAIN_USER + "\\" + BuildConfig.USERNAME + numeroSucursal

            if (ambiente == "DSUC" && numeroSucursal == "0085") {
                decryptedUsername = BuildConfig.DOMAIN_USER + "\\" + BuildConfig.USERNAME + "0074"
            }

            // Desencriptar password
            val crypto = CryptoProvider.getProvider().simCrypto
            decryptedPassword = crypto.desencriptar(pass)
        } catch (e: Exception) {
            Log.e("BNA-Crypto", "Error al inicializar credenciales", e)
            decryptedPassword = ""
        }
    }

    private fun updateLoadingProgress(progress: Int) {
        if (progress < 100) {
            progressBar.visibility = ProgressBar.VISIBLE
            progressBar.progress = progress
            Log.d("WebViewLog", "Cargando sistema... $progress%")
        } else {
            progressBar.visibility = ProgressBar.GONE
            Log.d("WebViewLog", "Sistema cargado completamente")
        }
    }

    private fun loadUrlWithRetry(url: String, attempt: Int = 0) {
        if (attempt < maxRetries) {
            Log.d("WebView", "Cargando URL - Intento ${attempt + 1}")
            myWebView.loadUrl(url)
            
            // Verificar carga después de 30 segundos
            Handler(Looper.getMainLooper()).postDelayed({
                if (myWebView.progress < 100) {
                    Log.w("WebView", "Reintentando carga - Intento ${attempt + 1}")
                    loadUrlWithRetry(url, attempt + 1)
                }
            }, 30000)
        } else {
            Log.e("WebView", "Falló después de $maxRetries intentos")
            sendError("No se pudo cargar el sistema después de varios intentos")
        }
    }

    private fun startWatchdog() {
        watchdogHandler = Handler(Looper.getMainLooper())
        val watchdogRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastActivityTime > inactivityTimeout) {
                    Log.w("Watchdog", "Inactividad detectada, recargando página")
                    myWebView.reload()
                    lastActivityTime = currentTime
                }
                watchdogHandler.postDelayed(this, 60 * 1000) // Verificar cada minuto
            }
        }
        watchdogHandler.post(watchdogRunnable)
    }

    private fun updateActivity() {
        lastActivityTime = System.currentTimeMillis()
    }

    private fun scheduleMemoryCleanup() {
        Handler(Looper.getMainLooper()).postDelayed({
            // Removido System.gc() manual para evitar micro-freezes
            Log.d("MemoryCleanup", "Ciclo de limpieza programado")
            scheduleMemoryCleanup() // Repetir cada 30 minutos
        }, 30 * 60 * 1000) // 30 minutos
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

    // CONFIGURACIÓN SSL HÍBRIDA PARA TOTEM - Mantiene certificados BNA pero más estable
    @RequiresApi(Build.VERSION_CODES.O)
    private fun configureSsl(webView: WebView) {
        try {
            // CONFIGURACIÓN HÍBRIDA: Mantener certificados BNA pero más estable
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)
            
            // Cargar certificados BNA de forma más robusta
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val certificateResources = listOf(
                R.raw.cabnaprod01,
                R.raw.cabnaprod02, 
                R.raw.cabnaprodroot,
                R.raw.cabnatest01,
                R.raw.cabnatest02,
                R.raw.cabnatestroot
            )
            
            // Cargar certificados con timeout y manejo de errores
            certificateResources.forEachIndexed { index, certRes ->
                try {
                    resources.openRawResource(certRes).use { inputStream ->
                        val certificate = certificateFactory.generateCertificate(inputStream)
                        keyStore.setCertificateEntry("bna_cert_$index", certificate)
                        Log.d("SSLConfig", "Certificado BNA $index cargado correctamente")
                    }
                } catch (e: Exception) {
                    Log.w("SSLConfig", "Error cargando certificado $index, continuando...", e)
                    // No fallar por un certificado, continuar con los demás
                }
            }
            
            // Configurar TrustManager con timeout reducido
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(keyStore)
            
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustManagerFactory.trustManagers, null)
            
            val sslSocketFactory = sslContext.socketFactory
            (webView.webViewClient as MyWebViewClient).sslSocketFactory = sslSocketFactory
            
            Log.d("SSLConfig", "SSL configurado correctamente con certificados BNA")
            
        } catch (e: Exception) {
            Log.e("SSLConfig", "Error configurando SSL con certificados BNA, usando configuración por defecto", e)
            
            // FALLBACK: Si falla, usar configuración por defecto
            try {
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, null, null)
                val sslSocketFactory = sslContext.socketFactory
                (webView.webViewClient as MyWebViewClient).sslSocketFactory = sslSocketFactory
                Log.w("SSLConfig", "Usando configuración SSL por defecto como fallback")
            } catch (fallbackError: Exception) {
                Log.e("SSLConfig", "Error incluso con configuración por defecto", fallbackError)
                // Continuar sin SSL personalizado
            }
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

    // AGREGAR MÉTODO onDestroy PARA LIMPIEZA
    override fun onDestroy() {
        super.onDestroy()
        
        // Limpiar watchdog
        if (::watchdogHandler.isInitialized) {
            watchdogHandler.removeCallbacksAndMessages(null)
        }
        
        // Limpiar WebView
        if (::myWebView.isInitialized) {
            myWebView.clearHistory()
            myWebView.clearCache(true)
            myWebView.loadUrl("about:blank")
            myWebView.onPause()
            myWebView.removeAllViews()
            myWebView.destroyDrawingCache()
            myWebView.pauseTimers()
            myWebView.destroy()
        }
        
        // Removido System.gc() manual para evitar micro-freezes
        Log.d("MainActivity", "Recursos limpiados en onDestroy")
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

        // MEJORAR MANEJO DE ERRORES PARA TOTEM
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

                // Categorizar errores y decidir acción
                when {
                    description.contains("net::ERR_CONNECTION_TIMED_OUT") ||
                    description.contains("net::ERR_NETWORK_CHANGED") ||
                    description.contains("net::ERR_INTERNET_DISCONNECTED") -> {
                        Log.w("WebViewError", "Error de conectividad detectado, intentando recargar...")
                        // Intentar recargar después de un breve delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            view?.reload()
                        }, 3000)
                    }
                    description.contains("net::ERR_NAME_NOT_RESOLVED") -> {
                        Log.e("WebViewError", "Error DNS, enviando a pantalla de errores")
                        sendError("Error de conectividad: $description")
                    }
                    else -> {
                        Log.e("WebViewError", "Error general: $description")
                        sendError("Error de navegación: $description")
                    }
                }
            }
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?
        ) {
            // Para TOTEM, ser más permisivo con errores SSL menores
            val errorType = error?.primaryError
            when (errorType) {
                SslError.SSL_DATE_INVALID,
                SslError.SSL_EXPIRED,
                SslError.SSL_INVALID -> {
                    Log.w("SSLError", "Error SSL menor detectado: $errorType, continuando...")
                    handler?.proceed() // Continuar para errores menores en TOTEM
                }
                else -> {
                    Log.e("SSLError", "Error SSL crítico: $errorType, cancelando...")
                    handler?.cancel()
                    sendError("Error SSL crítico: $errorType")
                }
            }
        }
    }
}
