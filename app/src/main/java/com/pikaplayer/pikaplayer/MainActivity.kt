package com.pikaplayer.pikaplayer

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    private lateinit var fingerPickerView: FingerPickerView
    private lateinit var gameModeTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        
        val rootLayout = FrameLayout(this)
        rootLayout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        fingerPickerView = FingerPickerView(this)
        rootLayout.addView(fingerPickerView)
        
        // Container for menu button and text
        val menuContainer = LinearLayout(this)
        menuContainer.orientation = LinearLayout.HORIZONTAL
        menuContainer.gravity = Gravity.CENTER_VERTICAL
        
        val containerParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        containerParams.gravity = Gravity.TOP or Gravity.START
        
        val density = resources.displayMetrics.density
        val marginStart = (16 * density).toInt()
        val marginTop = (80 * density).toInt()
        
        containerParams.setMargins(marginStart, marginTop, 0, 0)
        menuContainer.layoutParams = containerParams
        
        // Hamburger Menu Button (ImageButton)
        val menuButton = ImageButton(this)
        menuButton.setImageResource(R.drawable.ic_menu)
        menuButton.setBackgroundColor(Color.TRANSPARENT) // Make background transparent
        menuButton.scaleType = ImageView.ScaleType.CENTER_INSIDE
        menuButton.setPadding(16, 16, 16, 16) // Add touch target size
        
        menuButton.setOnClickListener { 
            showGameModeMenu(menuButton)
        }
        
        menuContainer.addView(menuButton)
        
        // Game Mode Text View
        gameModeTextView = TextView(this)
        gameModeTextView.setTextColor(Color.WHITE)
        gameModeTextView.textSize = 16f
        gameModeTextView.setPadding((8 * density).toInt(), 0, 0, 0)
        gameModeTextView.text = "Selected mode: Starting Player" // Initial text
        
        menuContainer.addView(gameModeTextView)
        rootLayout.addView(menuContainer)
        
        // Update text when mode changes
        fingerPickerView.setOnGameModeChangeListener { mode ->
            gameModeTextView.text = "Selected mode: ${mode.displayName}"
        }
        
        setContentView(rootLayout)
    }
    
    private fun showGameModeMenu(anchor: android.view.View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Starting Player")
        popup.menu.add("Group")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Starting Player" -> {
                    fingerPickerView.setGameMode(GameMode.STARTING_PLAYER)
                }
                "Group" -> {
                    fingerPickerView.setGameMode(GameMode.GROUP)
                }
            }
            true
        }
        popup.show()
    }
}
