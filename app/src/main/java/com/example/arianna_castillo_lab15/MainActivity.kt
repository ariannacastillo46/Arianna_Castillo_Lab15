package com.example.arianna_castillo_lab15

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.arianna_castillo_lab15.ui.theme.Arianna_Castillo_Lab15Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "TumorApp"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Arianna_Castillo_Lab15Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppRoot(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/** Carga el modelo en segundo plano y muestra cualquier error en pantalla. */
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var classifier by remember { mutableStateOf<TumorClassifier?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            classifier = withContext(Dispatchers.IO) {
                TumorClassifier(context.applicationContext)
            }
            Log.i(TAG, "Modelo cargado correctamente")
        } catch (t: Throwable) {
            Log.e(TAG, "Error cargando el modelo", t)
            error = t.toString()
        }
    }
    DisposableEffect(Unit) {
        onDispose { classifier?.close() }
    }

    val err = error
    val clf = classifier
    when {
        err != null -> Column(modifier.fillMaxSize().padding(16.dp)) {
            Text("Error al cargar el modelo:", color = Color.Red, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(err, color = Color.Red)
        }
        clf == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Cargando modelo…")
        }
        else -> TumorScreen(clf, modifier)
    }
}

private fun fmt(p: Float) = String.format(Locale.US, "%.4f", p)

// Obtiene el nombre del archivo elegido (por ejemplo "glioma_test.jpg")
private fun fileName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (col >= 0 && cursor.moveToFirst()) return cursor.getString(col)
    }
    return "Imagen subida"
}

@Composable
fun TumorScreen(classifier: TumorClassifier, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageName by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    // Selector de imágenes del teléfono (no necesita permisos)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val picked = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
            if (picked != null) {
                bitmap = picked
                imageName = fileName(context, uri)
                results = emptyList()
                error = null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Clasificador de tumores cerebrales (MRI)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = imageName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(220.dp)
            )
            Text(imageName, style = MaterialTheme.typography.bodySmall)
        } else {
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                Text("Sube una imagen de resonancia")
            }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedButton(onClick = { picker.launch("image/*") }) {
            Text("Subir imagen")
        }
        Spacer(Modifier.height(4.dp))

        Button(
            onClick = {
                current?.let {
                    try {
                        results = classifier.classify(it)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error clasificando", t)
                        error = t.toString()
                    }
                }
            },
            enabled = current != null
        ) {
            Text("Label Image")
        }
        Spacer(Modifier.height(16.dp))

        error?.let { Text(it, color = Color.Red) }

        if (results.isNotEmpty()) {
            val (topLabel, topProb) = results.first()
            Text(
                "Predicción: $topLabel (${fmt(topProb)})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            results.forEach { (label, prob) ->
                Text("$label : ${fmt(prob)}")
            }
        }
    }
}