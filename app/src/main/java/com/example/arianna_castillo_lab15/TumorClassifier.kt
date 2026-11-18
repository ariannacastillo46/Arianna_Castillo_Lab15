package com.example.arianna_castillo_lab15

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Carga model.tflite y labels.txt desde la carpeta assets
 * y clasifica una imagen de resonancia en 4 clases.
 */
class TumorClassifier(context: Context) {

    private val inputSize = 224

    // Nombres de las clases, en el mismo orden que la salida del modelo
    private val labels: List<String> = context.assets.open("labels.txt")
        .bufferedReader()
        .readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    private val interpreter: Interpreter

    init {
        // Leer el modelo desde assets a un buffer en memoria
        val bytes = context.assets.open("model.tflite").use { it.readBytes() }
        val modelBuffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        modelBuffer.put(bytes)
        modelBuffer.rewind()
        interpreter = Interpreter(modelBuffer)
    }

    /** Devuelve la lista (clase, probabilidad) ordenada de mayor a menor. */
    fun classify(bitmap: Bitmap): List<Pair<String, Float>> {
        // 1. Redimensionar a 224x224 (igual que en Colab)
        val argb = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val resized = Bitmap.createScaledBitmap(argb, inputSize, inputSize, true)

        // 2. Pasar los píxeles RGB (0-255) a un buffer de floats.
        //    No se normaliza: la capa Rescaling ya está dentro del modelo.
        val input = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in pixels) {
            input.putFloat(((p shr 16) and 0xFF).toFloat()) // R
            input.putFloat(((p shr 8) and 0xFF).toFloat())  // G
            input.putFloat((p and 0xFF).toFloat())          // B
        }
        input.rewind()

        // 3. Ejecutar el modelo: salida [1, 4] con las probabilidades
        val output = Array(1) { FloatArray(labels.size) }
        interpreter.run(input, output)

        // 4. Unir etiquetas y probabilidades, de mayor a menor
        return labels.zip(output[0].toList()).sortedByDescending { it.second }
    }

    fun close() = interpreter.close()
}