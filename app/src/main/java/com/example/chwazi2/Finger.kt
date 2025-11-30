package com.example.chwazi2

data class Finger(
    var x: Float,
    var y: Float,
    var color: Int,
    val startTime: Long = System.currentTimeMillis(),
    var groupColor: Int? = null
)
