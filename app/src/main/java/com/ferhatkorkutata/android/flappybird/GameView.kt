package com.ferhatkorkutata.android.flappybird

import android.content.Context
import android.content.DialogInterface
import android.graphics.*
import android.media.MediaPlayer
import android.os.Handler
import android.os.Message
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AlertDialog
import com.ferhatkorkutata.android.flappybird.extension.dp
import com.ferhatkorkutata.android.flappybird.model.Pipe
import java.lang.Thread.sleep
import java.util.*


class GameView : SurfaceView, Runnable, SurfaceHolder.Callback {

    companion object {
        const val GAME_OVER = 0x00
    }

    private lateinit var bmBird: Bitmap
    private var bmRotateBird: Bitmap? = null
    private lateinit var bmPipeUp: Bitmap
    private lateinit var bmPipeDown: Bitmap
    private lateinit var gameThread: Thread

    private lateinit var scoreMedia: MediaPlayer
    private lateinit var dieMedia: MediaPlayer
    private lateinit var wingMedia: MediaPlayer
    private lateinit var hitMedia: MediaPlayer
    private lateinit var themeMedia: MediaPlayer

    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var score = 0

    private var alive = true
    private var running = false

    private val groundHeight = 425

    private var birdPosX = 0.0f
    private var birdPosY = 0.0f
    private var birdVelocity =12.0f
    private val birdAcceleration = 1f
    private val birdJumpVelocity = -15f

    private val pipeGap = 450
    private val pipeBaseLength = 100
    private var pipeWidth = 0
    private val pipeVelocity = 9
    private val pipeInterval = 300
    private var pipes: MutableList<Pipe> = mutableListOf()
    private var pipeCount = 0

    private lateinit var alertDialog: AlertDialog.Builder
    private val msgHandler = Handler(Handler.Callback { message: Message ->
        when (message.what) {
            GAME_OVER -> {
                alertDialog.show()
                return@Callback true
            }
        }
        return@Callback false
    })

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init()
    }

    private fun init() {
        setZOrderOnTop(true)
        keepScreenOn = true

        bmBird = BitmapFactory.decodeResource(resources, R.drawable.ic_bird)
        bmPipeUp = BitmapFactory.decodeResource(resources, R.drawable.ic_pipe1)
        bmPipeDown = BitmapFactory.decodeResource(resources, R.drawable.ic_pipe2)

        pipeWidth = bmPipeUp.width

        holder.addCallback(this)
        holder.setFormat(PixelFormat.TRANSPARENT)

        scorePaint.apply {
            color = Color.WHITE
            textSize = 32f.dp
        }

        alertDialog = AlertDialog.Builder(this.context).apply {
            setTitle(R.string.game_over)
            setMessage(R.string.play_again)
            setCancelable(false)

            // Restart the game
            setPositiveButton(R.string.yes) { _: DialogInterface, _: Int ->
                startGame()
            }

            // Back to previous screen, i.e. the MainActivity
            setNegativeButton(R.string.no) { _: DialogInterface, _: Int ->
                (this@GameView.context as GameActivity).onBackPressed()
            }
        }
    }

    /**
     * When the view is resumed, restart the game.
     */
    fun resume() {
        if (holder.surface.isValid) {
            startGame()
        }
    }

    /**
     * When the view is paused, stop the game.
     */
    fun pause() {
        stopGame()
    }

    /**
     * Refresh the UI every 15 ms.
     */
    override fun run() {
        while (running && alive) {
            birdPosY += birdVelocity

            for (index in 0..pipeCount) {
                val pipe = getPipe(index)
                pipe.position -= pipeVelocity
                if (pipe.position <= -pipeWidth) {
                    pipe.position = measuredWidth
                    pipe.length = calculatePipeLength()
                    pipe.isPassed = false
                }
            }

            draw()

            alive = isAlive()

            if (birdVelocity <= 12.0f) {
                birdVelocity += birdAcceleration
            }
            sleep(15)
        }
        if (running && !alive) {
            // Sleep 0.3 second before falling down
            sleep(300)
            fall()
        }
    }

    /**
     * Fall down to the ground when game over.
     */
    private fun fall() {
        val groundPosY = measuredHeight - groundHeight
        var canvas: Canvas? = null

        // If not on the ground, need to draw the rotate animation
        if (birdPosY < groundPosY) {
            dieMedia.start()
            bmRotateBird = Bitmap.createBitmap(bmBird.width, bmBird.width, Bitmap.Config.ARGB_8888)
            canvas = Canvas(bmRotateBird!!)
        }

        var i = 1
        val pivot = bmBird.width.toFloat() / 2

        // Fall and rotate until it is on the ground
        while (birdPosY < groundPosY) {
            birdPosY += 15

            canvas?.apply {
                save()
                drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY)
                rotate(15f * i % 360, pivot, pivot)
                drawBitmap(bmBird, 0f, 0f, null)
                rotate(-15f * i % 360)
                restore()
            }
            draw()
            i++
        }
        hitMedia.start()
        bmRotateBird = null
        stopGame()
        msgHandler.sendEmptyMessage(GameActivity.GAME_OVER)
    }

    /**
     * Check if the bird is still alive.
     */
    private fun isAlive(): Boolean {
        //onResume() komutu çağrıldığında, pencere boyutu henüz kullanıma hazır değildir. Bu durumda,
        //birdPosX'in ekran genişliğine göre hesaplanması geçerli değildir, bu nedenle her zaman
        //buraya doğru dön
        if (width == 0 || height == 0) {
            return true
        }
        val birdRightPos = birdPosX + bmBird.width - 10
        val birdBottomPos = birdPosY + bmBird.height

        // Return false if the bird touches the top or bottom
        if (birdPosY <= 5 || birdPosY >= measuredHeight - groundHeight) {
            return false
        }
        for (index in 0..pipeCount) {
            val pipe = getPipe(index)
            // The pipe is in the area between bird and right border of game area
            if (pipe.position + pipeWidth >= birdPosX && pipe.position < measuredWidth) {
                if (birdRightPos >= pipe.position && birdPosX <= pipe.position + pipeWidth) {
                    if (birdPosY <= pipe.length || birdBottomPos >= pipe.length + pipeGap) {
                        return false
                    }
                }
            } else if (pipe.position < birdPosX && !pipe.isPassed) {
                score()
                pipe.isPassed = true
            }
        }
        return true
    }

    /**
     * Add score by 1, and player the sound
     */
    private fun score() {
        scoreMedia.start()
        score++
    }

    /**
     * Calculate a random length for a new pipe.
     */
    private fun calculatePipeLength(): Int {
        val baseline = (measuredHeight - pipeGap - groundHeight - pipeBaseLength * 2) / 2
        return (pipeBaseLength + baseline * Random().nextFloat()).toInt() + 10
    }

    /**
     * Handle the touch event.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            birdVelocity = birdJumpVelocity
            wingMedia.start()
            return true
        }
        return super.onTouchEvent(event)
    }

    /**
     * Draw the UI.
     */
    private fun draw() {
        if (holder.surface.isValid) {
            holder.apply {
                this.lockCanvas()?.let {

                    // Clear the canvas
                    it.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                    it.save()

                    // Draw pipes
                    for (index in 0..pipeCount) {
                        val pipe = getPipe(index)
                        it.drawBitmap(
                            bmPipeUp,
                            null,
                            Rect(
                                pipe.position, 0,
                                (pipe.position + bmPipeUp.width), pipe.length
                            ),
                            null
                        )
                        it.drawBitmap(
                            bmPipeDown, null,
                            Rect(
                                pipe.position, pipe.length + pipeGap,
                                pipe.position + bmPipeDown.width, measuredHeight - groundHeight + 65
                            ),
                            null
                        )
                    }

                    // Draw the bird
                    if (bmRotateBird != null) {
                        it.drawBitmap(bmRotateBird!!, birdPosX, birdPosY, null)
                    } else {
                        it.drawBitmap(bmBird, birdPosX, birdPosY, null)
                    }

                    // Draw score
                    it.drawText(score.toString(), 90f, 180f, scorePaint)

                    it.restore()
                    this.unlockCanvasAndPost(it)
                }
            }
        }
    }

    /**
     * Get pipe at index.
     */
    private fun getPipe(index: Int): Pipe {
        if (index >= pipes.size) {
            val pipe =
                Pipe(measuredWidth + (pipeWidth + pipeInterval) * index, calculatePipeLength())
            pipes.add(pipe)
        }
        return pipes[index]
    }

    /**
     * Stop the game.
     */
    private fun stopGame() {
        running = false
        alive = false
        try {
            gameThread.join(500)
        } catch (e: InterruptedException) {
        }
    }

    /**
     * Start the game.
     */
    private fun startGame() {
        resetData()
        gameThread = Thread(this)
        gameThread.start()
    }

    /**
     * Start the game when the view is created.
     */
    override fun surfaceCreated(holder: SurfaceHolder) {
        scoreMedia = MediaPlayer.create(context, R.raw.score).let {
            it.isLooping = false
            it!!
        }
        dieMedia = MediaPlayer.create(context, R.raw.die).let {
            it.isLooping = false
            it!!
        }
        wingMedia = MediaPlayer.create(context, R.raw.wing).let {
            it.isLooping = false
            it!!
        }
        hitMedia = MediaPlayer.create(context, R.raw.hit).let {
            it.isLooping = false
            it!!
        }
        themeMedia = MediaPlayer.create(context, R.raw.play_theme).let {
            it.isLooping = true
            it.start()
            it!!
        }

        startGame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    /**
     * Stop the game and release <code>Surface</code> when the view is destroyed.
     */
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopGame()
        scoreMedia.apply {
            stop()
            release()
        }
        dieMedia.apply {
            stop()
            release()
        }
        wingMedia.apply {
            stop()
            release()
        }
        hitMedia.apply {
            stop()
            release()
        }
        themeMedia.apply {
            stop()
            reset()
            release()
        }
        holder.surface.release()
    }

    /**
     * Reset data for every new game.
     */
    private fun resetData() {
        score = 0
        birdPosX = measuredWidth.toFloat() / 3
        birdPosY = (measuredHeight.toFloat() - groundHeight) / 3
        pipeCount = (measuredWidth - pipeWidth) / (pipeWidth + pipeInterval)
        birdVelocity = 12.0f
        pipes = mutableListOf()
        running = true
        alive = true
    }
}