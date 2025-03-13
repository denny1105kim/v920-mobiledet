package com.nexell.mobiledet.data

object ModelConstants {
    const val MODEL_NAME = "demo_mobiledet_ssd_int8_O2_MultiCore.nnc"

    val INPUT_DATA_TYPE = DataType.FLOAT32
    //    val INPUT_DATA_LAYER = LayerType.HWC
    val INPUT_DATA_LAYER = LayerType.CHW

    const val INPUT_SIZE_W = 320
    const val INPUT_SIZE_H = 320
    const val INPUT_SIZE_C = 3

    const val INPUT_CONVERSION_SCALE = 127.5F
    const val INPUT_CONVERSION_OFFSET = 127.5F

    val OUTPUT_DATA_TYPE = DataType.FLOAT32

    const val OUTPUT_SIZE_W = 7
    const val OUTPUT_SIZE_H = 10
    const val LABEL_FILE = "coco.txt"
}
