package com.app.speechapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.app.speechapp.ui.theme.SpeechappTheme
import java.util.Locale

private const val GOOGLE_TTS_ENGINE = "com.google.android.tts"
private const val START_LISTENING_UTTERANCE_ID = "start_listening"
private const val START_VOICE = "Kenali organ reproduksimu. Silahkan tekan button di tengah untuk memulai."
private const val FIRST_SCREEN_NOTE = "Silahkan pilih info apa yang ingin kamu ketahui:\n" +
        "1. Bagian bagian reproduksi.\n 2. Pubertas dan Menstruasi.\n 3. Permasalahan Organ Reproduksi.\n 4. Menjaga Kebersihan Organ Reproduksi.\n Silahkan berbicara sekarang"

class MainActivity : ComponentActivity() {

    private lateinit var tts: TextToSpeech
    private var _isTTsInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //checkGoogleTTS()
        setContent {
            var voiceText by remember { mutableStateOf("") }
            var isButtonEnable by remember { mutableStateOf(true) }
            val context = LocalContext.current

            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == Activity.RESULT_OK) {
                    val data = it.data
                    val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    voiceText = result?.get(0) ?: "No speech detected."
                    handleVoiceInput(voiceText)
                } else {
                    voiceText = "[Speech recognition failed.]"
                }
                isButtonEnable = true
            }

            LaunchedEffect(key1 = Unit) {
                if (!isGoogleTtsInstalled(context)) {
                    // Prompt user to install Google TTS
                    promptInstallGoogleTts(context)
                    promptSetGoogleTtsAsDefault(context)
                } else {
                    // Initialize TextToSpeech with Google TTS engine
                    tts = TextToSpeech(context,
                        { status ->
                            if (status == TextToSpeech.SUCCESS) {
                                _isTTsInitialized = true
                                changeToIndonesianVoice()
                                speak(START_VOICE)
                            } else {
                                /* no-op */
                            }
                        }, GOOGLE_TTS_ENGINE)

                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            /* no-op */
                        }

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == START_LISTENING_UTTERANCE_ID) {
                                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                                    tts.speak("tidak terdeteksi", TextToSpeech.QUEUE_FLUSH, null, null)
                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("market://details?id=com.google.android.googlequicksearchbox")
                                    }
                                    startActivity(installIntent)
                                } else {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Silahkan sebutkan nomor pilihan anda.")
                                    }
                                    launcher.launch(intent)
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java",
                            ReplaceWith("Log.v(\"\", \"error for \$utteranceId\")", "android.util.Log")
                        )
                        override fun onError(utteranceId: String?) {
                            Log.v("", "error for $utteranceId")
                        }
                    })
                }
            }
            SpeechappTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                      modifier = Modifier
                          .fillMaxSize()
                          .padding(innerPadding),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            voiceText.ifEmpty { FIRST_SCREEN_NOTE }, modifier = Modifier
                                .padding(32.dp)
                                .background(Color.Blue)
                                .padding(12.dp), color = Color.White)
                        Button(
                            enabled = isButtonEnable,
                            modifier = Modifier
                                .clip(CircleShape)
                                .height(250.dp)
                                .width(250.dp) ,
                            onClick = {
                                if (_isTTsInitialized) {
                                    voiceText = ""
                                    speak(FIRST_SCREEN_NOTE, START_LISTENING_UTTERANCE_ID)
                                    isButtonEnable = false
                                }
                            }) {
                            Text(text = "Bicara")
                        }
                    }
                }
            }
        }
    }

    private fun changeToIndonesianVoice() {
        val result = tts.setLanguage(Locale("id", "ID"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Prompt user to install language data
            val installIntent = Intent()
            installIntent.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
            installIntent.setPackage(GOOGLE_TTS_ENGINE)
            startActivity(installIntent)
        }
    }

    private fun speak(text: String, utteranceId: String? = null) {
        tts.setSpeechRate(1.0f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun handleVoiceInput(input: String) {
        val cleanedInput = input.replace(Regex("[^a-zA-Z0-9 ]"), "").trim().lowercase()
        when {
            cleanedInput.contains("satu") ||  cleanedInput.contains("1") -> {
                tts.speak("$cleanedInput, Bagian bagian reproduksi.", TextToSpeech.QUEUE_FLUSH, null, null)
            }
            cleanedInput.contains("dua") ||  cleanedInput.contains("2")-> {
                tts.speak("$cleanedInput, Pubertas dan Menstruasi.", TextToSpeech.QUEUE_FLUSH, null, null)
            }
            cleanedInput.contains("tiga") ||  cleanedInput.contains("3")-> {
                tts.speak("$cleanedInput, Permasalahan Organ Reproduksi.", TextToSpeech.QUEUE_FLUSH, null, null)
            }
            cleanedInput.contains("empat") ||  cleanedInput.contains("4")-> {
                tts.speak("$cleanedInput, Menjaga Kebersihan Organ Reproduksi.", TextToSpeech.QUEUE_FLUSH, null, null)
            }
            else -> {
                // Ask the user to repeat if input is not understood
                Log.d("yudith", cleanedInput)
                tts.speak("input is $cleanedInput", TextToSpeech.QUEUE_FLUSH, null, "RetryPrompt")
            }
        }
    }

    private fun isGoogleTtsInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.google.android.tts", 0)
            Log.d("mine", "isGoogleTtsInstalled: true")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d("mine", "isGoogleTtsInstalled: false")
            false
        }
    }

    private fun promptInstallGoogleTts(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts")
            setPackage("com.android.vending")
        }
        context.startActivity(intent)
    }

    private fun promptSetGoogleTtsAsDefault(context: Context) {
        val intent = Intent().apply {
            action = TextToSpeech.Engine.ACTION_CHECK_TTS_DATA
        }
        context.startActivity(intent)
        // Alternatively, direct them to the TTS settings
        val settingsIntent = Intent().apply {
            action = android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
        }
        context.startActivity(settingsIntent)
    }
}