package com.kt.webconteinerapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View

import android.widget.Button
import android.widget.EditText

import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


class ErroresActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pagina_error)
        // Obtener referencia a SharedPreferences
        val preferences = getSharedPreferences("mis_preferencias", Context.MODE_PRIVATE)
        val nombreEquipo = preferences.getString("nombreEquipo", "")
        val nombreServidor = preferences.getString("nombreServidor", "")


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

            // Lanzar la actividad principal
            startMainActivity(this)
            finish() // Opcional, dependiendo de si deseas que la actividad de ingreso de datos permanezca en la pila de actividades
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

