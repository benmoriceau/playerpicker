package com.example.chwazi2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Remove the title bar and make it full screen for better experience
        supportActionBar?.hide()
        
        val fingerPickerView = FingerPickerView(this)
        setContentView(fingerPickerView)
    }
}
