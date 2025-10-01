package com.kt.webconteinerapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


class ErroresActivity : AppCompatActivity() {
    // AGREGAR TIMEOUT PARA BOTÓN DE REINTENTAR
    private var lastRetryTime = 0L
    private val retryTimeout = 5000L // 5 segundos entre reintentos
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pagina_error)
        // Obtener referencia a SharedPreferences
        val preferences = getSharedPreferences("mis_preferencias", Context.MODE_PRIVATE)
        val nombreEquipo = preferences.getString("nombreEquipo", "")
        val nombreServidor = preferences.getString("nombreServidor", "")

        // Obtén el TextView
        val versionNameTextView: TextView = findViewById(R.id.versionNameTextView)

        // Establece el versionName usando BuildConfig
        val versionName = BuildConfig.VERSION_NAME
        versionNameTextView.text = "V$versionName"

        // Recuperar el Intent que inició esta actividad
        val intent = intent
        // Obtener el texto enviado
        val receivedText = intent.getStringExtra("EXTRA_TEXT")

        // Mostrar el texto en un TextView
        val textViewMsjError = findViewById<TextView>(R.id.TextViewMensajeError)
        val textViewSucursal = findViewById<TextView>(R.id.TextViewSucursal)
        val textViewNombreDispositivo = findViewById<TextView>(R.id.TextViewNombreDispositivo)
        textViewSucursal.text = "Servidor: $nombreServidor"
        textViewNombreDispositivo.text = "Dispositivo: $nombreEquipo"
        textViewMsjError.text = receivedText

        val buttonReintentar = findViewById<Button>(R.id.ButtonReintentar)

        buttonReintentar.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            
            // Verificar si ha pasado suficiente tiempo desde el último intento
            if (currentTime - lastRetryTime >= retryTimeout) {
                lastRetryTime = currentTime
                
                Log.d("ErroresActivity", "Reintentando conexión...")
                
                // Deshabilitar botón temporalmente para evitar clicks múltiples
                buttonReintentar.isEnabled = false
                buttonReintentar.text = "Reintentando..."
                
                // Habilitar botón después del timeout
                Handler(Looper.getMainLooper()).postDelayed({
                    buttonReintentar.isEnabled = true
                    buttonReintentar.text = "Reintentar"
                }, retryTimeout)
                
                // Lanzar la actividad principal
                startMainActivity(this)
                finish()
            } else {
                val remainingTime = (retryTimeout - (currentTime - lastRetryTime)) / 1000
                Log.w("ErroresActivity", "Debe esperar $remainingTime segundos antes de reintentar")
            }
        }

    }


    fun startMainActivity(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        context.startActivity(intent)
    }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            //I need to do someing.
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        val decorView = window.decorView
        decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE // sirve para sacar el header
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }
}

