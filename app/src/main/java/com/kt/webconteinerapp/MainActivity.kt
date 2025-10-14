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
import android.webkit.WebResourceResponse
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
import android.net.NetworkCapabilities
import android.view.ViewGroup
import android.widget.Toast
import java.net.InetSocketAddress
import java.net.Socket


class MainActivity : AppCompatActivity() {
    private lateinit var myWebView: WebView
    private lateinit var progressBar: ProgressBar
    private val CAMERA_PERMISSION_REQUEST_CODE = 100
    private var lastLoadedUrl: String? = null

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

        pass = BuildConfig.PASSWORD

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

        myWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                updateLoadingProgress(newProgress)
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }
        }


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

        // Configurar el certificado SSL
        configureSsl(myWebView)

        // Configurar WebView para debugging en desarrollo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false) // false en producción
        }

        // Cargar URL con sistema de reintentos
        // Validar configuración antes de construir la URL
        if (nombreServidor.isNullOrBlank() || nombreEquipo.isNullOrBlank()) {
            Log.e("Config", "Faltan parámetros de configuración: servidor o equipo vacío")
            progressBar.visibility = ProgressBar.GONE
            handleError(
                type = "CONFIG",
                message = "Faltan parámetros de configuración del tótem.",
                detail = "Valores de servidor o equipo nulos.",
                code = "CFG001"
            )
            return
        }
        val versionName = BuildConfig.VERSION_NAME
        val url = "https://$nombreServidor/BNA.KT.Totem.Tab/Default.aspx?nombreEquipo=$nombreEquipo&apkVersion=V$versionName"
        // Verificar conectividad antes de iniciar la carga para evitar mostrar páginas de error por defecto


        if (!canConnectToServer()) {
            Log.e("Connectivity", "Sin conexión de red detectada al iniciar")
            progressBar.visibility = ProgressBar.GONE
            handleError(
                type = "RED",
                message = "No hay conexión disponible. Verifique la red del tótem.",
                detail = "Sin conexión Wi-Fi/Ethernet detectada.",
                code = "NET001"
            )
        } else {
            loadUrlWithRetry(url)
        }

        // Iniciar sistemas de monitoreo
        startWatchdog()
        scheduleMemoryCleanup()

        // Configurar listener para detectar actividad (Se bloquea el scroll para que no permita mover la pantalla)
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

    @RequiresApi(Build.VERSION_CODES.M)
    private fun loadUrlWithRetry(url: String, attempt: Int = 0) {

            if (!canConnectToServer()) {
                Log.e("WebView", "Sin conexión de red, derivando a pantalla de errores")
                progressBar.visibility = ProgressBar.GONE
                handleError(
                    type = "RED",
                    message = "No hay conexión disponible. Verifique la red del tótem.",
                    detail = "No se pudo resolver el nombre del servidor.",
                    code = "NET002"
                )
            }

        if (attempt < maxRetries) {
            Log.d("WebView", "Cargando URL - Intento ${attempt + 1}")
            try {
                myWebView.setNetworkAvailable(true)
            } catch (_: Exception) {}
            progressBar.visibility = ProgressBar.VISIBLE
            myWebView.loadUrl(url)

            // Verificar carga después de 12 segundos para evitar spinner prolongado tras reconexión
            Handler(Looper.getMainLooper()).postDelayed({
                if (myWebView.progress < 100) {
                    Log.w("WebView", "Reintentando carga - Intento ${attempt + 1}")
                    loadUrlWithRetry(url, attempt + 1)
                }
            }, 12000)
        } else {
            Log.e("WebView", "Falló después de $maxRetries intentos")
            progressBar.visibility = ProgressBar.GONE
            handleError(
                type = "RED",
                message = "No se pudo conectar con el servidor tras múltiples intentos.",
                detail = "Reintentos agotados en WebView.",
                code = "NET002"
            )
        }
    }

    private fun startWatchdog() {
        watchdogHandler = Handler(Looper.getMainLooper())
        val watchdogRunnable = object : Runnable {
            @RequiresApi(Build.VERSION_CODES.M)
            override fun run() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastActivityTime > inactivityTimeout) {
                    Log.w("Watchdog", "Inactividad detectada, recargando página")

                    checkServerReachability() { reachable ->
                        if (reachable) {
                            try {
                                myWebView.stopLoading()
                                if (!lastLoadedUrl.isNullOrEmpty()) {
                                    myWebView.loadUrl(lastLoadedUrl!!)
                                } else {
                                    Log.w("Watchdog", "No hay URL previa, no se puede recargar")
                                }
                            } catch (e: Exception) {
                                Log.e("Watchdog", "Error al recargar: ${e.message}")
                            }
                        }
                        else{
                            handleError(
                                type = "RED",
                                message = "No hay conexión disponible. Verifique la red del tótem.",
                                detail = "No se pudo resolver el nombre del servidor.",
                                code = "NET003"
                            )
                        }
                        }

                    lastActivityTime = currentTime
                }
                watchdogHandler.postDelayed(this, 60 * 1000)
            }
        }
        watchdogHandler.post(watchdogRunnable)
    }


    private fun updateActivity() {
        lastActivityTime = System.currentTimeMillis()
    }

    private fun scheduleMemoryCleanup() {
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("MemoryCleanup", "Limpieza de memoria ejecutada")
            if (!isFinishing) scheduleMemoryCleanup()
        }, 30 * 60 * 1000)//30 minutos
    }


    fun sendError(error: String) {
        try {
            progressBar.visibility = ProgressBar.GONE
            myWebView.stopLoading()
            myWebView.loadUrl("about:blank")
        } catch (e: Exception) {
            Log.w("sendError", "No se pudo ocultar spinner/limpiar WebView: ${e.message}")
        }

        val intent = Intent(this, ErroresActivity::class.java).apply {
            putExtra("EXTRA_TEXT", error)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(intent)
        overridePendingTransition(0, 0) // 👈 sin animación
        finish() // 👈 elimina la actividad anterior completamente
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun canConnectToServer(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        // Considerar conectividad de red local sin salida a Internet (Wi‑Fi/Ethernet), y celular por compatibilidad
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    override fun onPause() {
        super.onPause()

        // Detener watchdog (si está activo)
        if (::watchdogHandler.isInitialized) {
            watchdogHandler.removeCallbacksAndMessages(null)
            Log.d("MainActivity", "Watchdog detenido en onPause()")
        }

        // Pausar WebView de forma segura
        if (::myWebView.isInitialized) {
            try {
                myWebView.onPause()
                myWebView.pauseTimers()
                Log.d("MainActivity", "WebView pausado correctamente")
            } catch (e: Exception) {
                Log.w("MainActivity", "Error al pausar WebView: ${e.message}")
            }
        }
    }


    override fun onResume() {
        super.onResume()

        // Reanudar WebView si ya estaba inicializado
        if (::myWebView.isInitialized) {
            try {
                myWebView.onResume()
                myWebView.resumeTimers()
                Log.d("MainActivity", "WebView reanudado correctamente")
            } catch (e: Exception) {
                Log.w("MainActivity", "Error al reanudar WebView: ${e.message}")
            }
        }

        // Reiniciar watchdog si es necesario
        if (::watchdogHandler.isInitialized) {
            watchdogHandler.removeCallbacksAndMessages(null)
        }
        startWatchdog()
        Log.d("MainActivity", "Watchdog reiniciado en onResume()")
    }
    //METODO onDestroy PARA LIMPIEZA
    override fun onDestroy() {
        super.onDestroy()

        // Detener watchdog completamente
        if (::watchdogHandler.isInitialized) {
            watchdogHandler.removeCallbacksAndMessages(null)
            Log.d("MainActivity", "Watchdog detenido en onDestroy()")
        }

        // Limpiar y destruir WebView
        if (::myWebView.isInitialized) {
            try {
                (myWebView.parent as? ViewGroup)?.removeView(myWebView)
                myWebView.stopLoading()
                myWebView.clearHistory()
                myWebView.clearCache(true)
                myWebView.loadUrl("about:blank")
                myWebView.removeAllViews()
                myWebView.destroy()
                Log.d("MainActivity", "WebView destruido correctamente")
            } catch (e: Exception) {
                Log.w("MainActivity", "Error al destruir WebView: ${e.message}")
            }
            Log.d("MainActivity", "Recursos limpiados en onDestroy")

        }
    }


    private fun safeLog(tag: String, msg: String, level: Int = Log.DEBUG) {
        if (BuildConfig.DEBUG) {
            when (level) {
                Log.ERROR -> Log.e(tag, msg)
                Log.WARN -> Log.w(tag, msg)
                else -> Log.d(tag, msg)
            }
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d("MainActivity", "onNewIntent recibido: reintentar carga")
        // Reutilizar parámetros desde SharedPreferences
        val preferences = getSharedPreferences("mis_preferencias", Context.MODE_PRIVATE)
        val nombreEquipo = preferences.getString("nombreEquipo", "")
        val nombreServidor = preferences.getString("nombreServidor", "")

        // Validar configuración antes de construir la URL en reintento
        if (nombreServidor.isNullOrBlank() || nombreEquipo.isNullOrBlank()) {
            Log.e("Config", "Faltan parámetros de configuración en reintento")
            progressBar.visibility = ProgressBar.GONE
            try {
                myWebView.stopLoading()
                myWebView.loadUrl("about:blank")
            } catch (_: Exception) {}
            handleError(
                type = "CONFIG",
                message = "Faltan parámetros de configuración del tótem.",
                detail = "Valores de servidor o equipo nulos.",
                code = "CFG001"
            )
            return
        }
        val versionName = BuildConfig.VERSION_NAME
        val url = "https://$nombreServidor/BNA.KT.Totem.Tab/Default.aspx?nombreEquipo=$nombreEquipo&apkVersion=V$versionName"

        // Chequeo de conectividad real antes de reintentar
        checkServerReachability() { reachable ->
            if (reachable) {
                Log.i("ConnectivityCheck", "Servidor accesible desde red corporativa")
                // Reintento de carga controlado tras reconexión
                try {
                    myWebView.setNetworkAvailable(true)
                    myWebView.resumeTimers()
                    myWebView.onResume()
                    myWebView.clearCache(true)
                    // Reaplicar configuración SSL para evitar estados inconsistentes tras reconexión
                    configureSsl(myWebView)
                } catch (_: Exception) {}
                Handler(Looper.getMainLooper()).postDelayed({
                    loadUrlWithRetry(url)
                }, 1500)
            } else {
                Log.w("ConnectivityCheck", "No se pudo alcanzar el servidor")
                progressBar.visibility = ProgressBar.GONE
                try {
                    myWebView.stopLoading()
                    myWebView.loadUrl("about:blank")
                } catch (_: Exception) {}
                handleError(
                    type = "RED",
                    message = "No hay conexión disponible. Verifique la red del tótem.",
                    detail = "No se pudo resolver el nombre del servidor.",
                    code = "NET002"
                )
            }
        }
    }

    private fun checkServerReachability(port: Int = 443, callback: (Boolean) -> Unit) {
        val preferences = getSharedPreferences("mis_preferencias", Context.MODE_PRIVATE)
        val host = preferences.getString("nombreServidor", null)
        Thread {
            val result = try {
                // Resolución DNS
                val address = java.net.InetAddress.getByName(host)
                Log.i("ConnectivityCheck", "📡 DNS OK: $host -> ${address.hostAddress}")

                // Intentar conexión TCP con timeout
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(address, port), 2000)
                    Log.i("ConnectivityCheck", "✅ TCP OK: $host:$port accesible")
                    true
                }
            } catch (e: java.net.UnknownHostException) {
                Log.e("ConnectivityCheck", "❌ DNS FAIL: no se pudo resolver el host $host -> ${e.message}")
                false
            } catch (e: java.net.ConnectException) {
                Log.e("ConnectivityCheck", "❌ TCP FAIL (ConnectException): ${e.message}")
                false
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("ConnectivityCheck", "⏱️ TIMEOUT: el host $host:$port no respondió a tiempo")
                false
            } catch (e: java.net.NoRouteToHostException) {
                Log.e("ConnectivityCheck", "🚫 SIN RUTA: no hay camino hacia $host:$port")
                false
            } catch (e: java.io.IOException) {
                Log.e("ConnectivityCheck", "💥 IO FAIL (${e.javaClass.simpleName}): ${e.message}")
                false
            } catch (e: Exception) {
                Log.e("ConnectivityCheck", "❌ TCP FAIL (${e.javaClass.simpleName}): ${e.message}")
                false
            }

            // Retornar al hilo principal (UI) con el resultado
            runOnUiThread { callback(result) }
        }.start()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun configureSsl(webView: WebView) {
        try {
            // --- CONFIGURACIÓN HÍBRIDA DE CERTIFICADOS BNA ---
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)

            val certificateFactory = CertificateFactory.getInstance("X.509")
            val certificateResources = listOf(
                R.raw.cabnaprod01,
                R.raw.cabnaprod02,
                R.raw.cabnaprodroot,
                R.raw.cabnatest01,
                R.raw.cabnatest02,
                R.raw.cabnatestroot
            )

            certificateResources.forEachIndexed { index, certRes ->
                try {
                    resources.openRawResource(certRes).use { inputStream ->
                        val certificate = certificateFactory.generateCertificate(inputStream)
                        keyStore.setCertificateEntry("bna_cert_$index", certificate)
                        Log.d("SSLConfig", "Certificado BNA $index cargado correctamente")
                    }
                } catch (e: Exception) {
                    Log.w("SSLConfig", "Error cargando certificado $index, continuando...", e)
                }
            }

            // Configurar TrustManager
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(keyStore)

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustManagerFactory.trustManagers, null)
            val sslSocketFactory = sslContext.socketFactory

            val client = webView.webViewClient
            if (client is MyWebViewClient) {
                client.sslSocketFactory = sslSocketFactory
                Log.i("SSLConfig", "SSL configurado correctamente con certificados BNA")
            } else {
                throw IllegalStateException("El WebViewClient no es MyWebViewClient (actual: ${client::class.java.simpleName})")
            }

        } catch (e: Exception) {
            Log.e("SSLConfig", "Fallo crítico en configuración SSL", e)

            handleError(
                type = "SSL",
                message = "no se pudieron cargar los certificados del servidor.",
                detail = e.message,
                code = "NET002"
            )
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
            if (url != null && url != "about:blank") {
                lastLoadedUrl = url
            }
            progressBar.visibility = View.GONE
        }

        // MEJORAR MANEJO DE ERRORES PARA TOTEM
        @RequiresApi(Build.VERSION_CODES.M)
        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            // Evitar mostrar la página de error por defecto y redirigir al flujo de errores
            if (request?.isForMainFrame == true) {
                val description = error?.description?.toString() ?: "Error de navegación"
                Log.e("WebViewError", "Error principal: ${error?.errorCode} - $description")
                view?.stopLoading()
                view?.loadUrl("about:blank")
                progressBar.visibility = View.GONE
                when {
                    description.contains("ERR_INTERNET_DISCONNECTED", ignoreCase = true) ||
                            description.contains("ERR_NETWORK_CHANGED", ignoreCase = true) ||
                            description.contains("ERR_CONNECTION_TIMED_OUT", ignoreCase = true) -> {
                        handleError(
                            type = "RED",
                            message = "Sin red/internet.",
                            detail = description,
                            code = "DNS001"
                        )
                    }
                    description.contains("ERR_NAME_NOT_RESOLVED", ignoreCase = true) -> {
                        handleError(
                            type = "RED",
                            message = "No se pudo resolver el nombre del servidor.",
                            detail = description,
                            code = "DNS001"
                        )
                    }
                    else -> {
                        handleError(
                            type = "NAVEGACIÓN",
                            message = "Ocurrió un error al cargar la página.",
                            detail = description,
                            code = "NAV001"
                        )
                    }
                }
                return
            }
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
                        }, 300)
                    }
                    description.contains("net::ERR_NAME_NOT_RESOLVED") -> {
                        Log.e("WebViewError", "Error DNS, enviando a pantalla de errores")
                        handleError(
                            type = "RED",
                            message = "No se pudo resolver el nombre del servidor.",
                            detail = description,
                            code = "DNS001"
                        )
                    }
                    else -> {
                        Log.e("WebViewError", "Error general: $description")
                        handleError(
                            type = "NAVEGACIÓN",
                            message = "Ocurrió un error al cargar la página.",
                            detail = description,
                            code = "NAV001"
                        )
                    }
                }
            }
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?
        ) {
            // Derivar SIEMPRE a ErroresActivity ante problemas SSL para evitar mostrar pantallas de error
            val errorType = error?.primaryError
            Log.e("SSLError", "Error SSL detectado: $errorType, cancelando navegación...")
            handler?.cancel()
            view?.stopLoading()
            view?.loadUrl("about:blank")
            progressBar.visibility = View.GONE
            handleError(
                type = "SSL",
                message = "Error de certificado SSL durante la conexión.",
                detail = "Tipo de error: $errorType",
                code = "SSL001"
            )
        }
        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            if (request?.isForMainFrame == true) {
                val status = errorResponse?.statusCode ?: -1
                Log.e("WebViewHttpError", "HTTP $status en el frame principal")
                view?.stopLoading()
                view?.loadUrl("about:blank")
                progressBar.visibility = View.GONE
                handleError(
                    type = "HTTP",
                    message = "El servidor respondió con un error HTTP.",
                    detail = "Código: $status",
                    code = "HTTP$status"
                )
            }
        }
    }

    private fun handleError(
        type: String,
        message: String,
        detail: String? = null,
        code: String = "GEN000"
    ) {
        val errorMessage = buildString {
            append("[$type]: $message")
            detail?.let {
                append("\n\nDetalle técnico:\n$it")
            }
            append("\n\nCódigo: $code")
        }
        Log.e("AppError", "Error estandarizado -> $errorMessage")
        sendError(errorMessage)
    }
}

