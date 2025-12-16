package com.example.crimicam.facerecognitionnetface.models

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer

// Mask Detection model
// Source -> https://github.com/achen353/Face-Mask-Detector
class MaskDetectionModel( context: Context ) {

    val MASK = "mask"
    val NO_MASK = "no mask"

    private val imgSize = 224
    private val numClasses = 2
    private val classIndexToLabel = mapOf(
        0 to MASK ,
        1 to NO_MASK ,
    )
    private val modelName = "mask_detector.tflite"

    private var interpreter : Interpreter
    private var gpuDelegate: GpuDelegate? = null
    private val imageTensorProcessor = ImageProcessor.Builder()
        .add( ResizeOp( imgSize , imgSize , ResizeOp.ResizeMethod.BILINEAR ) )
        .add( NormalizeOp( 127.5f ,127.5f ) )
        .build()

    init {
        // Initialize TFLiteInterpreter
        val interpreterOptions = Interpreter.Options().apply {
            try {
                // Check if GPU delegate is supported
                val compatList = CompatibilityList()
                if (compatList.isDelegateSupportedOnThisDevice) {
                    // Create GPU delegate with best options for this device
                    val delegateOptions = compatList.bestOptionsForThisDevice
                    gpuDelegate = GpuDelegate(delegateOptions)
                    addDelegate(gpuDelegate)
                    Log.i("MaskDetection", "GPU delegate enabled successfully")
                } else {
                    // Fallback to CPU with multiple threads
                    numThreads = 4
                    Log.i("MaskDetection", "GPU not supported, using CPU with 4 threads")
                }
            } catch (e: Exception) {
                // If GPU initialization fails, fallback to CPU
                Log.e("MaskDetection", "Error initializing GPU delegate: ${e.message}")
                numThreads = 4
            }
            setUseXNNPACK(true)
        }
        interpreter = Interpreter(FileUtil.loadMappedFile( context, modelName ) , interpreterOptions )
    }


    // Predict the emotion given the cropped Bitmap
    fun detectMask( image : Bitmap ) : String {
        return classIndexToLabel[ runModel( processTensorImage( image )).argmax() ]!!
    }


    // Kotlin Extension function for arg max.
    // See https://kotlinlang.org/docs/extensions.html
    private fun FloatArray.argmax() : Int {
        return this.indexOfFirst { it == this.maxOrNull()!! }
    }


    // Run the mask detection model.
    private fun runModel(inputs: Any): FloatArray {
        val t1 = System.currentTimeMillis()
        val modelOutputs = Array( 1 ){ FloatArray( numClasses ) }
        interpreter.run( inputs, modelOutputs )
        Log.i( "Performance" , "Mask detection model inference speed in ms : ${System.currentTimeMillis() - t1}")
        return modelOutputs[ 0 ]
    }


    // Process the given bitmap and convert it to a ByteBuffer
    private fun processTensorImage( image  : Bitmap ) : ByteBuffer {
        return imageTensorProcessor.process( TensorImage.fromBitmap( image ) ).buffer
    }

    // Clean up resources when done
    fun close() {
        interpreter.close()
        gpuDelegate?.close()
    }

}