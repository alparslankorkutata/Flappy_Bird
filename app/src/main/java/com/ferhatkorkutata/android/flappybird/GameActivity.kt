package com.ferhatkorkutata.android.flappybird

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.ferhatkorkutata.android.flappybird.databinding.ActivityGameBinding

class GameActivity : AppCompatActivity() {

    companion object {
        const val GAME_OVER = 0x00
    }

    private lateinit var binding: ActivityGameBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN

        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        binding.gameView.resume()
    }

    override fun onPause() {
        super.onPause()
        binding.gameView.pause()
    }
}