// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.nexell.mobiledet.executor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.nfc.Tag
import android.os.SystemClock
import android.util.Log
import com.nexell.mobiledet.data.DetectionResult
import com.nexell.mobiledet.data.DataType
import com.nexell.mobiledet.data.LayerType
import com.nexell.mobiledet.data.ModelConstants
import com.nexell.mobiledet.enn_type.BufferSetInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder


@Suppress("IMPLICIT_CAST_TO_ANY")
@OptIn(ExperimentalUnsignedTypes::class)
class ModelExecutor(
    var threshold: Float = 0.5F,
    val context: Context,
    val executorListener: ExecutorListener?
) {
    private external fun ennInitialize()
    private external fun ennDeinitialize()
    private external fun ennOpenModel(filename: String): Long
    private external fun ennCloseModel(modelId: Long)
    private external fun ennAllocateAllBuffers(modelId: Long): BufferSetInfo
    private external fun ennReleaseBuffers(bufferSet: Long, bufferSize: Int)
    private external fun ennExecute(modelId: Long)
    private external fun ennMemcpyHostToDevice(bufferSet: Long, layerNumber: Int, data: ByteArray)
    private external fun ennMemcpyDeviceToHost(bufferSet: Long, layerNumber: Int): ByteArray

    private var modelId: Long = 0
    private var bufferSet: Long = 0
    private var nInBuffer: Int = 0
    private var nOutBuffer: Int = 0

    init {
        System.loadLibrary("enn_jni")
        copyNNCFromAssetsToInternalStorage(MODEL_NAME)
        getLabels()
        setupENN()
    }

    private fun setupENN() {
        // Initialize ENN
        ennInitialize()

        // Open model
        val fileAbsoluteDirectory = File(context.filesDir, MODEL_NAME).absolutePath
        modelId = ennOpenModel(fileAbsoluteDirectory)

        // Allocate all required buffers
        val bufferSetInfo = ennAllocateAllBuffers(modelId)
        bufferSet = bufferSetInfo.buffer_set
        nInBuffer = bufferSetInfo.n_in_buf
        nOutBuffer = bufferSetInfo.n_out_buf
    }

    fun process(image: Bitmap) {
        // Process Image to Input Byte Array
        val input = preProcess(image)
        // Copy Input Data
        ennMemcpyHostToDevice(bufferSet, 0, input)

        var inferenceTime = SystemClock.uptimeMillis()
        // Model execute
        ennExecute(modelId)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
        // Copy Output Data
        val output = ennMemcpyDeviceToHost(bufferSet, nInBuffer)
        Log.i("ModelExecutor", "Output size: ${output.size}")


        executorListener?.onResults(
            postProcess(output, image), inferenceTime
        )
    }

    fun closeENN() {
        // Release a buffer array
        ennReleaseBuffers(bufferSet, nInBuffer + nOutBuffer)
        // Close a Model and Free all resources
        ennCloseModel(modelId)
        // Destructs ENN process
        ennDeinitialize()
    }

    private fun preProcess(image: Bitmap): ByteArray {
        val byteArray = when (INPUT_DATA_TYPE) {
            DataType.UINT8 -> {
                throw IllegalArgumentException("Unsupported input data type: ${INPUT_DATA_TYPE}")
            }
            DataType.FLOAT32 -> {
                val data = convertBitmapToFloatArray(image, INPUT_DATA_LAYER)
                Log.i("preprocess", "input data layer : $INPUT_DATA_LAYER")
                Log.i("preprocess", "input data type : $INPUT_DATA_TYPE")
                Log.i("preprocess","input size: ${data.size}")
                val byteBuffer = ByteBuffer.allocate(data.size * Float.SIZE_BYTES)
                Log.i("preprocess", "last input size : ${data.size * Float.SIZE_BYTES}")
                byteBuffer.order(ByteOrder.nativeOrder())
                byteBuffer.asFloatBuffer().put(data)
                byteBuffer.array()
            }

            else -> {
                throw IllegalArgumentException("Unsupported input data type: ${INPUT_DATA_TYPE}")
            }
        }
        return byteArray
    }

    private fun postProcess(modelOutput: ByteArray, image: Bitmap): List<DetectionResult> {
        Log.i("postprocess", "output data type : $OUTPUT_DATA_TYPE")
        val output = when (OUTPUT_DATA_TYPE) {
            DataType.FLOAT32 -> {
                val byteBuffer = ByteBuffer.wrap(modelOutput).order(ByteOrder.nativeOrder())
                val floatBuffer = byteBuffer.asFloatBuffer()
                val floatArray = FloatArray(floatBuffer.remaining())
                floatBuffer.get(floatArray)
                floatArray
            }
            else -> {
                throw IllegalArgumentException("Unsupported output data type: ${OUTPUT_DATA_TYPE}")
            }
        }
        Log.i("postprocess", "output size : ${output.size}")
        val scaleFactorW = OUTPUT_SIZE_W.toFloat() /image.width
        val scaleFactorH = OUTPUT_SIZE_H.toFloat() / image.height
        val resizeScales: Float = maxOf(scaleFactorW, scaleFactorH)
        Log.i("postprocess", "resizeScales : ${resizeScales}")
        val outputWithIndex = output.withIndex()
        val outputString = outputWithIndex.joinToString("\n") { "Index: ${it.index}, Value: ${it.value}" }
        Log.i("postprocess", "outputWithIndex: \n$outputString")
        val result: ArrayList<DetectionResult> = arrayListOf()


        for (i in 0 until OUTPUT_SIZE_H) {
            val index = i * OUTPUT_SIZE_W
            if (output[index + 2] >= threshold) {
                val classId = output[index + 1].toInt()
                val confidence = output[index + 2]
                val left = output[index + 3] * OUTPUT_SIZE_W * scaleFactorW
                val top = output[index + 4] * OUTPUT_SIZE_H * scaleFactorH
                val right = output[index + 5] * OUTPUT_SIZE_W * scaleFactorW
                val bottom = output[index + 6] * OUTPUT_SIZE_H * scaleFactorH


                // DetectionResult 생성
                result.add(
                    DetectionResult(
                        outputWithIndex
                            .filter { it.index in index + 5 until index + OUTPUT_SIZE_H }
                            .maxByOrNull { it.value }
                            ?.let { Pair(labelList[classId-1], confidence) }!!,
                            boundingBox = RectF(left, top, right, bottom)))

            }
        }

        // confidence가 threshold 이상인 결과만 반환

        Log.d("result------>", "${result.filter { it.score.second >= threshold }}")
        return result.filter { it.score.second >= threshold }
    }

    private fun convertBitmapToFloatArray(
        image: Bitmap, layerType: Enum<LayerType> = LayerType.HWC
    ): FloatArray {
        val totalPixels = INPUT_SIZE_H * INPUT_SIZE_W
        val pixels = IntArray(totalPixels)

        image.getPixels(
            pixels,
            0,
            INPUT_SIZE_W,
            0,
            0,
            INPUT_SIZE_W,
            INPUT_SIZE_H
        )

        val floatArray = FloatArray(totalPixels * INPUT_SIZE_C)
        val offset: IntArray
        val stride: Int

        if (layerType == LayerType.CHW) {
            offset = intArrayOf(0, totalPixels, 2 * totalPixels)
            stride = 1
        } else {
            offset = intArrayOf(0, 1, 2)
            stride = 3
        }

        for (i in 0 until totalPixels) {
            val color = pixels[i]
            floatArray[i * stride + offset[0]] = ((((color shr 16) and 0xFF)
                    - INPUT_CONVERSION_OFFSET)
                    / INPUT_CONVERSION_SCALE)
            floatArray[i * stride + offset[1]] = ((((color shr 8) and 0xFF)
                    - INPUT_CONVERSION_OFFSET)
                    / INPUT_CONVERSION_SCALE)
            floatArray[i * stride + offset[2]] = ((((color shr 0) and 0xFF)
                    - INPUT_CONVERSION_OFFSET)
                    / INPUT_CONVERSION_SCALE)
        }

        return floatArray
    }

    private fun copyNNCFromAssetsToInternalStorage(filename: String) {
        try {
            val inputStream = context.assets.open(filename)
            val outputFile = File(context.filesDir, filename)
            val outputStream = FileOutputStream(outputFile)
            val buffer = ByteArray(2048)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            inputStream.close()
            outputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getLabels() {
        try {
            context.assets.open(LABEL_FILE)
                .bufferedReader().use { reader -> labelList = reader.readLines() }
            Log.d("get label------","get label : ${LABEL_FILE}")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    interface ExecutorListener {
        fun onError(error: String)
        fun onResults(
            detectionResult: List<DetectionResult>, inferenceTime: Long
        )
    }

    companion object {
        var labelList: List<String> = mutableListOf()

        private const val MODEL_NAME = ModelConstants.MODEL_NAME

        private val INPUT_DATA_LAYER = ModelConstants.INPUT_DATA_LAYER
        private val INPUT_DATA_TYPE = ModelConstants.INPUT_DATA_TYPE

        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
        private const val INPUT_SIZE_C = ModelConstants.INPUT_SIZE_C

        private const val INPUT_CONVERSION_SCALE = ModelConstants.INPUT_CONVERSION_SCALE
        private const val INPUT_CONVERSION_OFFSET = ModelConstants.INPUT_CONVERSION_OFFSET

        private val OUTPUT_DATA_TYPE = ModelConstants.OUTPUT_DATA_TYPE

        private const val OUTPUT_SIZE_W = ModelConstants.OUTPUT_SIZE_W
        private const val OUTPUT_SIZE_H = ModelConstants.OUTPUT_SIZE_H

        private const val LABEL_FILE = ModelConstants.LABEL_FILE
    }
}
