package com.dee.android.pbl.fakefluent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class VoiceToTextManager(val context: Context) {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US") // 明确指定美式英语
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // 开启中间结果，识别更快
    }

    fun startListening(onResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("STT", "当前系统不支持语音识别！")
            // 如果这里报错，说明你得安装“Google”应用或者 VIVO 的语音引擎不兼容
            return
        }
        // 确保在主线程操作
        Handler(Looper.getMainLooper()).post {
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = data?.get(0) ?: ""
                    Log.d("STT", "最终结果: $text")
                    onResult(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val data = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = data?.get(0) ?: ""
                    Log.d("STT", "中间过程: $text")
                    onResult(text) // 实时显示文字
                }

                override fun onError(error: Int) {
                    // 关键：如果识别失败，这里会打印错误码
                    // 7 = 没听清, 9 = 权限没开, 5 = 网络问题
                    Log.e("STT", "识别错误码: $error")
                }

                override fun onReadyForSpeech(params: Bundle?) { Log.d("STT", "准备好了，请说话") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            recognizer.startListening(intent)
        }
    }

    fun stopListening() {
        recognizer.stopListening()
    }
}