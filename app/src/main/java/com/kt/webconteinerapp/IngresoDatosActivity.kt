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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.DecimalFormat
import androidx.appcompat.app.AlertDialog
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

class IngresoDatosActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar

    private val CAMERA_PERMISSION_REQUEST_CODE = 100
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ingreso_datos)


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


        //Obtener referencia a SharedPreferences
        val preferences = getSharedPreferences("mis_preferencias", Context.MODE_PRIVATE)

        // Obtener el valor guardado utilizando la clave específica
        val nombreEquipo = preferences.getString(
            "nombreEquipo",
            ""
        ) // Se utiliza un valor predeterminado vacío si no se encuentra ningún valor guardado
        val nombreServidor = preferences.getString("nombreServidor", "")
        val ambiente = preferences.getString("ambiente", "")
        if (nombreEquipo != null && nombreServidor != null && ambiente != null) {
            if (nombreEquipo.isNotEmpty() && nombreServidor.isNotEmpty() && ambiente.isNotEmpty()) {
                startMainActivity(this)
                finish() // Opcional, dependiendo de si deseas que la actividad de ingreso de datos permanezca en la pila de actividades
            }
        }

        // Obtener el Spinner
        val environmentSpinner: Spinner = findViewById(R.id.environmentSpinner)

        // Crear un ArrayAdapter usando el array de strings y un diseño de spinner por defecto
        ArrayAdapter.createFromResource(
            this, R.array.environments_array, android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Especificar el layout que se usará cuando la lista de opciones aparezca
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Aplicar el adaptador al spinner
            environmentSpinner.adapter = adapter
        }

        var ambienteSeleccionado = ""
        // Cambiar el color de la opción seleccionada a blanco
        environmentSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                if (view is TextView) {
                    view.setTextColor(resources.getColor(android.R.color.white))
                    view.textSize = 22f
                    ambienteSeleccionado = parent.getItemAtPosition(position).toString()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Otra acción cuando no se selecciona nada
            }
        }
        // Obtener el EditText
        val nombreDispositivoEditText: EditText = findViewById(R.id.EditTextName)

        // Configurar el teclado numérico programáticamente
        nombreDispositivoEditText.inputType = InputType.TYPE_CLASS_NUMBER

        // Añadir un TextWatcher para validar la entrada numérica
        nombreDispositivoEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // No es necesario hacer nada aquí
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // No es necesario hacer nada aquí
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Validar que solo se ingrese un número
                if (s != null && s.isNotEmpty()) {
                    val regex = Regex("^[0-9]*$")
                    if (!s.matches(regex)) {
                        nombreDispositivoEditText.error = "Solo se permiten números"
                    }
                }
            }
        })

        // Obtener el EditText
        val numeroSucursalEditText: EditText = findViewById(R.id.EditTextSucursal)

        // Configurar el teclado numérico programáticamente
        numeroSucursalEditText.inputType = InputType.TYPE_CLASS_NUMBER
        // Añadir un TextWatcher para validar la entrada numérica
        numeroSucursalEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // No es necesario hacer nada aquí
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // No es necesario hacer nada aquí
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Validar que solo se ingrese un número
                if (s != null && s.isNotEmpty()) {
                    val regex = Regex("^[0-9]*$")
                    if (!s.matches(regex)) {
                        numeroSucursalEditText.error = "Solo se permiten números"
                    }
                }
            }
        })

        val nombreTablet = findViewById<EditText>(R.id.EditTextName)
        val sucursalTablet = findViewById<EditText>(R.id.EditTextSucursal)
        val buttonGuardar = findViewById<Button>(R.id.ButtonGuardar)
        progressBar = findViewById(R.id.progressBar)
        progressBar.visibility = ProgressBar.INVISIBLE


        buttonGuardar.setOnClickListener {
            progressBar.visibility = ProgressBar.VISIBLE

            // Obtener los valores de los EditText
            val nombreTabletText = nombreTablet.text.toString().trim()
            val sucursalTabletText = completarConCeros(sucursalTablet.text.toString().trim())


            if (nombreTabletText.isEmpty() || sucursalTabletText.isEmpty() || nombreTabletText == "0000" || sucursalTabletText == "0000") {
                // Mostrar mensaje de error si uno o ambos campos están vacíos
                Toast.makeText(this, "Ambos campos deben ser completados", Toast.LENGTH_SHORT)
                    .show()
                progressBar.visibility = ProgressBar.GONE

            } else {
                val df = DecimalFormat("0000")
                val nroTablet = df.format(nombreTabletText.toInt())
                val nroSucursal = df.format(sucursalTabletText.toInt())
                Log.d("Variables", "nroTablet: $nroTablet")
                Log.d("Variables", "nroSucursal: $nroSucursal")

                if (nroSucursal.length < 4 || nroTablet.length < 4) {
                    showAlertDialog("El largo ingresado es incorrecto")

                    //Toast.makeText(this, "El largo ingresado es incorrecto", Toast.LENGTH_SHORT)
                    //    .show()
                    progressBar.visibility = ProgressBar.GONE

                } else {

                    val nombreCompleto = "T" + nroTablet + "SC" + nroSucursal

                    val nombreServidor =
                        resolverNombreServidor(nroTablet, nroSucursal, ambienteSeleccionado)

                    if (nombreServidor == "ERROR") {
                        showAlertDialog("No es posible conectarse al servidor, ¿se encuentra conectado a la red wifi correcta?")

                        //Toast.makeText(this, "No es posible conectarse al servidor, ¿se encuentra conectado a la red wifi correcta?", Toast.LENGTH_LONG).show()
                    } else if (nombreCompleto.length != 11) {
                        // Toast.makeText(this, "Error al validar campos", Toast.LENGTH_SHORT).show()
                        showAlertDialog("El largo ingresado es incorrecto")

                    } else {
                        // Guardar los valores utilizando SharedPreferences u otro método de persistencia de datos
                        // Obtener un editor para modificar SharedPreferences
                        val editor = preferences.edit()
                        // Guardar el dato con una clave específica
                        editor.putString("nombreEquipo", nombreCompleto)
                        editor.putString("nombreServidor", nombreServidor)
                        editor.putString("ambiente", ambienteSeleccionado)
                        // Aplicar los cambios
                        editor.apply()

                        // Lanzar la actividad principal
                        startMainActivity(this)
                        finish() // Opcional, dependiendo de si deseas que la actividad de ingreso de datos permanezca en la pila de actividades
                    }
                    progressBar.visibility = ProgressBar.GONE
                }
            }
        }
    }


    fun resolverNombreServidor(nombreTablet: String, numeroSuc: String, ambiente: String): String {
        Log.d("Variables", "ambiente: $ambiente")

        val sucursalServidor = when (ambiente) {
            "Producción" -> {
                if (numeroSuc == "0085") {
                    "SNL01SC0085.SUC.BNA.NET"
                } else {
                    if (getPingResponse("SMF01SC${numeroSuc}.SUC.BNA.NET")) {
                        "SMF01SC${numeroSuc}.SUC.BNA.NET"
                    } else if (getPingResponse("SMF02SC${numeroSuc}.SUC.BNA.NET")) {
                        "SMF02SC${numeroSuc}.SUC.BNA.NET"
                    } else "ERROR"
                }
            }

            "Testing" -> {
                when (numeroSuc) {
                    "0085" -> "TNL01SC0085.TSUC.TBNA.NET"
                    "0064" -> "TMF02SC6021.TSUC.TBNA.NET"
                    "0070" -> "TMF02SC6020.TSUC.TBNA.NET"
                    else -> {
                        if (getPingResponse("TMF01SC${numeroSuc}.TSUC.TBNA.NET")) {
                            "TMF01SC${numeroSuc}.TSUC.TBNA.NET"
                        } else if (getPingResponse("TMF02SC${numeroSuc}.TSUC.TBNA.NET")) {
                            "TMF02SC${numeroSuc}.TSUC.TBNA.NET"
                        } else "ERROR"
                    }
                }
            }

            "Desarrollo" -> {
//                if (numeroSuc == "0085" || numeroSuc == "6220") {
                if (numeroSuc == "0085") {
                    "dap13cc0001.DCC.DBNA.NET"
                } else if (numeroSuc == "6220") {
                    "dmf01sc6220.dsuc.dbna.net"
                } else {
                    if (isReachableBySocket("DMF02SC${numeroSuc}.DSUC.DBNA.NET")) {
                        "DMF02SC${numeroSuc}.DSUC.DBNA.NET"
                    } else if (isReachableBySocket("DMF01SC${numeroSuc}.DSUC.DBNA.NET")) {
                        "DMF01SC${numeroSuc}.DSUC.DBNA.NET"
                    } else "ERROR"
                }
            }

            else -> {
                "ERROR"
            }
        }

        return sucursalServidor
    }

    private fun validateIP(ip: String?): Boolean {
        if (ip == null) {
            return false
        }
        return ip.startsWith("10.") || ip.startsWith("172.")
    }


    private fun getPingResponse(address: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -w 5 $address")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            val output = StringBuilder()
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()
            val exitValue = process.waitFor()
            if (exitValue == 0) {
                // Parse the IP address from the ping output
                val regex = Regex("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})")
                val matchResult = regex.find(output.toString())
                val ipAddress = matchResult?.groups?.get(0)?.value
                validateIP(ipAddress)
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun isReachableBySocket(host: String): Boolean {
        return try {
            val host1 = "https://$host/BNA.KT.Totem.Tab/Default.aspx?nombreEquipo=T0001SC6220"
            val port = 443
            val timeout = 2000 // 2 segundos

            val socket = Socket()
            socket.connect(InetSocketAddress(host1, port), timeout)
            socket.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun startMainActivity(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        context.startActivity(intent)
    }

    fun completarConCeros(editText: String): String {
        val textoIngresado = editText
        val longitudActual = textoIngresado.length
        val digitosFaltantes = 4 - longitudActual

        return if (digitosFaltantes > 0) {
            "0".repeat(digitosFaltantes) + textoIngresado
        } else {
            textoIngresado
        }
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
        decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE // sirve para sacar el header
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    private fun showAlertDialog(message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(message)
            .setPositiveButton("Aceptar") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
}

