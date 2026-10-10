package com.example.ggdigitalmixer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.drawable.GradientDrawable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : Activity() {
    private val bg = Color.rgb(12, 17, 29)
    private val panel = Color.rgb(24, 32, 50)
    private val accent = Color.rgb(0, 225, 190)
    private val white = Color.rgb(240, 245, 255)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var audioRecord: AudioRecord? = null
    private var micMonitorTrack: AudioTrack? = null
    private var musicPlayer: MediaPlayer? = null
    private var musicPrepared = false
    private var micMonitorEnabled = true
    private var monitorThread: Thread? = null
    private val monitorRunning = AtomicBoolean(false)
    private var selectedMusic: Uri? = null
    private var selectedMusicName = "No track selected"
    private var recordingActive = false
    private var lastMicLevel = 0
    private var eq: Equalizer? = null
    private var reverb: PresetReverb? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var audioSessionId = 0
    private lateinit var micValue: TextView
    private lateinit var statusText: TextView
    private lateinit var monitorButton: Button
    private lateinit var musicNameView: TextView
    private lateinit var levelBar: ProgressBar
    private lateinit var bassSeek: SeekBar
    private lateinit var midSeek: SeekBar
    private lateinit var trebleSeek: SeekBar
    private lateinit var reverbSwitch: Switch
    private lateinit var echoSwitch: Switch
    private lateinit var musicSeek: SeekBar
    private lateinit var musicValue: TextView
    private lateinit var micGainSeek: SeekBar
    private lateinit var masterSeek: SeekBar
    private lateinit var micGainValue: TextView
    private lateinit var masterValue: TextView
    private lateinit var noiseSwitch: Switch
    private lateinit var playMusicButton: Button
    private lateinit var monitorSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(label("GG DIGITAL MIXER", 25, accent, true))
        content.addView(label("LIVE AUDIO • MIXER CONSOLE", 11, Color.LTGRAY, true).apply {
            setPadding(0, dp(3), 0, dp(14))
        })
        statusText = label("●  READY • AUDIO ENGINE IDLE", 12, accent, true).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(panel, 12)
        }
        content.addView(statusText, matchWrap())

        content.addView(section("MICROPHONE INPUT"))
        val micCard = card()
        micCard.addView(label("MIC INPUT LEVEL", 14, white, true))
        micValue = label("0%", 13, accent, true)
        micCard.addView(micValue)
        levelBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        micCard.addView(levelBar, matchWrap())
        micGainValue = label("Mic gain: 100% (neutral)", 12, white, true)
        micCard.addView(micGainValue)
        micGainSeek = SeekBar(this).apply {
            max = 200
            progress = 100
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    micGainValue.text = "Mic gain: ${p}%${if (p == 100) " (neutral)" else ""}"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        micCard.addView(micGainSeek)
        val micActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        monitorButton = Button(this).apply {
            text = "START MIC METER"
            setOnClickListener { toggleMicMeter() }
        }
        micActions.addView(monitorButton, LinearLayout.LayoutParams(0, -2, 1f))
        micCard.addView(micActions)
        content.addView(micCard, spaced())

        content.addView(section("MUSIC PLAYER"))
        val musicCard = card()
        musicNameView = label(selectedMusicName, 12, white, false)
        musicCard.addView(musicNameView)
        val musicActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        musicActions.addView(Button(this).apply {
            text = "CHOOSE AUDIO"
            setOnClickListener { chooseAudio() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        playMusicButton = Button(this).apply {
            text = "PLAY / PAUSE"
            setOnClickListener { toggleMusicPlayback() }
        }
        musicActions.addView(playMusicButton, LinearLayout.LayoutParams(0, -2, 1f))
        musicActions.addView(Button(this).apply {
            text = "CLEAR"
            setOnClickListener {
                selectedMusic = null
                releaseMusicPlayer()
                playMusicButton.text = "PLAY MUSIC"
                selectedMusicName = "No track selected"
                musicNameView.text = selectedMusicName
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        musicCard.addView(musicActions)
        musicValue = label("Music level: 70%", 12, accent, true)
        musicCard.addView(musicValue)
        musicSeek = seek(70) { p ->
            musicValue.text = "Music level: $p%"
            try { musicPlayer?.setVolume(p / 100f, p / 100f) } catch (_: Exception) {}
        }
        musicCard.addView(musicSeek)
        masterValue = label("Mic monitor output: 80%", 12, white, true)
        musicCard.addView(masterValue)
        masterSeek = seek(80) { p ->
            masterValue.text = "Mic monitor output: $p%"
            try { micMonitorTrack?.setVolume(p / 100f) } catch (_: Exception) {}
        }
        musicCard.addView(masterSeek)
        content.addView(musicCard, spaced())

        content.addView(section("EQUALIZER"))
        val eqCard = card()
        bassSeek = addToneControl(eqCard, "Bass", 50)
        midSeek = addToneControl(eqCard, "Mid", 50)
        trebleSeek = addToneControl(eqCard, "Treble", 50)
        content.addView(eqCard, spaced())

        content.addView(section("EFFECTS & TOOLS"))
        val fxCard = card()
        echoSwitch = effectSwitch("Acoustic echo cancellation (for mic capture)", false)
        reverbSwitch = effectSwitch("Preset reverb (when supported)", false)
        fxCard.addView(echoSwitch)
        fxCard.addView(reverbSwitch)
        monitorSwitch = effectSwitch("Monitor microphone to phone output (use headphones)", true).apply {
            setOnCheckedChangeListener { _, checked -> micMonitorEnabled = checked }
        }
        fxCard.addView(monitorSwitch)
        noiseSwitch = effectSwitch("Noise suppression (when supported)", false).apply {
            setOnCheckedChangeListener { _, checked -> toggleNoiseSuppressor(checked) }
        }
        fxCard.addView(noiseSwitch)
        val presetActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        presetActions.addView(Button(this).apply {
            text = "SAVE PRESET"
            setOnClickListener { savePreset() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        presetActions.addView(Button(this).apply {
            text = "LOAD PRESET"
            setOnClickListener { loadPreset() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        fxCard.addView(presetActions)
        fxCard.addView(Button(this).apply {
            text = "RESET CONTROLS"
            setOnClickListener { resetControls() }
        })
        content.addView(fxCard, spaced())

        content.addView(label(
            "Audio tools: music playback and microphone monitoring are separate output paths. This app does not combine audio into a virtual microphone for other apps or stream services. Use headphones to prevent feedback; USB mic support depends on Android device routing.",
            11, Color.LTGRAY, false
        ).apply { setPadding(dp(4), dp(14), dp(4), dp(8)) })

        setContentView(root)
        echoSwitch.setOnCheckedChangeListener { _, checked -> toggleEchoCanceler(checked) }
        reverbSwitch.setOnCheckedChangeListener { _, checked -> toggleReverb(checked) }
    }

    private fun toggleMicMeter() {
        if (monitorRunning.get()) {
            stopMicMeter()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 20)
            return
        }
        startMicMeter()
    }

    private fun startMicMeter() {
        try {
            val sampleRate = 44100
            val minBytes = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBytes <= 0) {
                toast("Mic capture is not supported on this device")
                return
            }
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBytes, sampleRate / 2)
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                toast("Unable to initialize microphone")
                return
            }
            audioRecord = recorder
            audioSessionId = recorder.audioSessionId
            setupAudioEffects(audioSessionId)
            val outMin = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (outMin > 0) {
                micMonitorTrack = AudioTrack.Builder()
                    .setAudioAttributes(android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(maxOf(outMin, sampleRate / 2))
                    .setTransferMode(AudioTrack.MODE_STREAM).build()
                micMonitorTrack?.play()
                setupOutputEffects(micMonitorTrack?.audioSessionId ?: 0)
            }
            recorder.startRecording()
            monitorRunning.set(true)
            monitorButton.text = "STOP MIC METER"
            statusText.text = "●  MICROPHONE METER ACTIVE"
            monitorThread = Thread {
                val buffer = ShortArray(1024)
                while (monitorRunning.get()) {
                    val read = try { recorder.read(buffer, 0, buffer.size) } catch (_: Exception) { -1 }
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            val s = buffer[i].toDouble()
                            sum += s * s
                        }
                        // Apply input gain BEFORE writing samples to the monitor output.
                        // Keep RMS calculation based on captured input so the meter remains stable.
                        val gain = micGainSeek.progress / 100f
                        if (micMonitorEnabled) {
                            for (i in 0 until read) {
                                val amplified = (buffer[i] * gain)
                                    .toInt()
                                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                buffer[i] = amplified.toShort()
                            }
                            try { micMonitorTrack?.write(buffer, 0, read) } catch (_: Exception) {}
                        }
                        val rms = sqrt(sum / read)
                        val pct = ((20.0 * kotlin.math.log10(maxOf(1.0, rms) / 32768.0) + 60.0) * 100.0 / 60.0)
                            .toInt().coerceIn(0, 100)
                        mainHandler.post {
                            if (monitorRunning.get()) {
                                lastMicLevel = pct
                                micValue.text = "$pct%"
                                levelBar.progress = pct
                            }
                        }
                    } else {
                        Thread.sleep(40)
                    }
                }
            }.apply { name = "MicMeterThread"; start() }
        } catch (e: Exception) {
            stopMicMeter()
            toast("Mic error: ${e.message ?: "unknown"}")
        }
    }

    private fun stopMicMeter() {
        monitorRunning.set(false)
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        try { micMonitorTrack?.pause() } catch (_: Exception) {}
        try { micMonitorTrack?.flush() } catch (_: Exception) {}
        try { micMonitorTrack?.release() } catch (_: Exception) {}
        micMonitorTrack = null
        try { eq?.release() } catch (_: Exception) {}; eq = null
        try { reverb?.release() } catch (_: Exception) {}; reverb = null
        try { echoCanceler?.release() } catch (_: Exception) {}; echoCanceler = null
        try { noiseSuppressor?.release() } catch (_: Exception) {}; noiseSuppressor = null
        try { agc?.release() } catch (_: Exception) {}; agc = null
        monitorThread = null
        monitorButton.text = "START MIC METER"
        statusText.text = "●  READY • AUDIO ENGINE IDLE"
        micValue.text = "$lastMicLevel%"
    }

    private fun setupAudioEffects(sessionId: Int) {
        // Release old effect handles before attaching a new recorder session.
        try { echoCanceler?.release() } catch (_: Exception) {}; echoCanceler = null
        try { noiseSuppressor?.release() } catch (_: Exception) {}; noiseSuppressor = null
        try { agc?.release() } catch (_: Exception) {}; agc = null
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)
                echoCanceler?.enabled = echoSwitch.isChecked
            }
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(sessionId)
                agc?.enabled = false
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)
                noiseSuppressor?.enabled = false
            }
        } catch (_: Exception) {}
    }

    private fun toggleEchoCanceler(enabled: Boolean) {
        try {
            if (echoCanceler == null && monitorRunning.get()) setupAudioEffects(audioSessionId)
            echoCanceler?.enabled = enabled
            toast(if (enabled) "Echo cancellation requested" else "Echo cancellation off")
        } catch (_: Exception) { toast("Echo cancellation unavailable on this device") }
    }

    private fun toggleNoiseSuppressor(enabled: Boolean) {
        try {
            if (noiseSuppressor == null && monitorRunning.get()) setupAudioEffects(audioSessionId)
            noiseSuppressor?.enabled = enabled
            toast(if (enabled) "Noise suppression requested" else "Noise suppression off")
        } catch (_: Exception) { toast("Noise suppression unavailable on this device") }
    }

    private fun setupOutputEffects(sessionId: Int) {
        if (sessionId <= 0) return
        try {
            eq?.release()
            eq = Equalizer(0, sessionId).apply { enabled = true }
            applyEqualizer()
            reverb?.release()
            reverb = PresetReverb(0, sessionId).apply {
                preset = PresetReverb.PRESET_MEDIUMROOM
                enabled = reverbSwitch.isChecked
            }
        } catch (_: Exception) { }
    }

    private fun applyEqualizer() {
        try {
            val e = eq ?: return
            val bands = e.numberOfBands.toInt()
            for (i in 0 until bands) {
                val center = e.getCenterFreq(i.toShort()) / 1000f
                val controlRange = e.bandLevelRange
                val slider = when {
                    center < 250 -> bassSeek.progress
                    center < 2500 -> midSeek.progress
                    else -> trebleSeek.progress
                }
                val maxBoost = minOf(controlRange[1].toInt(), 1200)
                val minCut = maxOf(controlRange[0].toInt(), -1200)
                val level = (minCut + (slider / 100f) * (maxBoost - minCut)).toInt()
                e.setBandLevel(i.toShort(), level.toShort())
            }
        } catch (_: Exception) { }
    }

    private fun toggleReverb(enabled: Boolean) {
        try {
            reverb?.enabled = enabled
            toast(if (enabled) "Reverb enabled on mic monitor (if supported)" else "Reverb off")
        } catch (_: Exception) { toast("Reverb unavailable on this device") }
    }

    private fun toggleMusicPlayback() {
        val player = musicPlayer
        if (player == null) {
            if (selectedMusic == null) { toast("Choose an audio file first"); return }
            try {
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setOnAudioFocusChangeListener { change ->
                            if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                                try { musicPlayer?.pause(); playMusicButton.text = "RESUME MUSIC" } catch (_: Exception) {}
                            }
                        }.build()
                    audioManager.requestAudioFocus(focusRequest)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                }
                musicPlayer = MediaPlayer().apply {
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                    setDataSource(this@MainActivity, selectedMusic!!)
                    setOnPreparedListener {
                        musicPrepared = true
                        it.setVolume(musicSeek.progress / 100f, musicSeek.progress / 100f)
                        it.start()
                        playMusicButton.text = "PAUSE MUSIC"
                        statusText.text = "●  MUSIC PLAYING"
                    }
                    setOnCompletionListener { playMusicButton.text = "PLAY MUSIC" }
                    setOnErrorListener { _, _, _ -> toast("This audio format cannot be played"); true }
                    prepareAsync()
                }
                playMusicButton.text = "LOADING…"
            } catch (e: Exception) { toast("Cannot play audio: ${e.message ?: "error"}") }
        } else if (musicPrepared) {
            try {
                if (player.isPlaying) { player.pause(); playMusicButton.text = "RESUME MUSIC" }
                else { player.start(); playMusicButton.text = "PAUSE MUSIC"; statusText.text = "●  MUSIC PLAYING" }
            } catch (_: Exception) { toast("Playback error") }
        }
    }

    private fun chooseAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        try { startActivityForResult(intent, 30) } catch (_: Exception) { toast("File picker unavailable") }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 30 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            selectedMusic = uri
            try {
                contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && ix >= 0) selectedMusicName = c.getString(ix) ?: "Selected audio"
                }
            } catch (_: Exception) { selectedMusicName = "Selected audio file" }
            musicNameView.text = selectedMusicName
            releaseMusicPlayer()
            playMusicButton.text = "PLAY MUSIC"
            toast("Audio ready. Tap PLAY MUSIC to start")
        }
    }

    private fun addToneControl(parent: LinearLayout, name: String, initial: Int): SeekBar {
        parent.addView(label("$name: 0 dB (center)", 13, white, true))
        val valueView = parent.getChildAt(parent.childCount - 1) as TextView
        val bar = seek(initial) { p ->
            val db = ((p - 50) * 0.24).toInt()
            valueView.text = "$name: ${if (db > 0) "+" else ""}$db dB"
            applyEqualizer()
        }
        parent.addView(bar)
        return bar
    }

    private fun savePreset() {
        val prefs = getSharedPreferences("mixer_preset", MODE_PRIVATE)
        prefs.edit()
            .putInt("music", musicSeek.progress)
            .putInt("mic_gain", micGainSeek.progress)
            .putInt("master", masterSeek.progress)
            .putBoolean("noise_suppress", noiseSwitch.isChecked)
            .putInt("bass", bassSeek.progress)
            .putInt("mid", midSeek.progress)
            .putInt("treble", trebleSeek.progress)
            .putBoolean("echo_cancel", echoSwitch.isChecked)
            .putBoolean("reverb", reverbSwitch.isChecked)
            .putBoolean("mic_monitor", micMonitorEnabled)
            .apply()
        toast("Preset saved on this phone")
    }

    private fun loadPreset() {
        val p = getSharedPreferences("mixer_preset", MODE_PRIVATE)
        musicSeek.progress = p.getInt("music", 70)
        micGainSeek.progress = p.getInt("mic_gain", 100).coerceIn(0, 200)
        masterSeek.progress = p.getInt("master", 80)
        bassSeek.progress = p.getInt("bass", 50)
        midSeek.progress = p.getInt("mid", 50)
        trebleSeek.progress = p.getInt("treble", 50)
        echoSwitch.isChecked = p.getBoolean("echo_cancel", false)
        reverbSwitch.isChecked = p.getBoolean("reverb", false)
        noiseSwitch.isChecked = p.getBoolean("noise_suppress", false)
        monitorSwitch.isChecked = p.getBoolean("mic_monitor", true)
        toast("Preset loaded")
    }

    private fun resetControls() {
        musicSeek.progress = 70
        micGainSeek.progress = 100
        masterSeek.progress = 80
        bassSeek.progress = 50
        midSeek.progress = 50
        trebleSeek.progress = 50
        echoSwitch.isChecked = false
        reverbSwitch.isChecked = false
        noiseSwitch.isChecked = false
        monitorSwitch.isChecked = true
        toast("Controls reset")
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(panel, 16)
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun effectSwitch(name: String, checked: Boolean): Switch =
        Switch(this).apply {
            text = name
            textSize = 12f
            setTextColor(white)
            isChecked = checked
            setPadding(dp(2), dp(7), dp(2), dp(7))
        }

    private fun seek(initial: Int, changed: (Int) -> Unit): SeekBar = SeekBar(this).apply {
        max = 100
        progress = initial
        progressTintList = android.content.res.ColorStateList.valueOf(accent)
        thumbTintList = android.content.res.ColorStateList.valueOf(accent)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { changed(p) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun section(title: String): View = label(title, 12, accent, true).apply {
        setPadding(dp(3), dp(17), 0, dp(8))
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun spaced(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(8)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 20 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startMicMeter()
        } else if (requestCode == 20) toast("Microphone permission is required for the level meter")
    }

    private fun releaseMusicPlayer() {
        try { musicPlayer?.stop() } catch (_: Exception) {}
        try { musicPlayer?.release() } catch (_: Exception) {}
        musicPlayer = null
        musicPrepared = false
    }

    override fun onDestroy() {
        stopMicMeter()
        releaseMusicPlayer()
        try { eq?.release() } catch (_: Exception) {}
        try { reverb?.release() } catch (_: Exception) {}
        try { echoCanceler?.release() } catch (_: Exception) {}
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        try { agc?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
