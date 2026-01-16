package com.dee.android.pbl.fakefluent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image // 必须有这个
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource // 必须有这个
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // 渐变背景
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF2196F3), Color(0xFF0D47A1))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.splash_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(180.dp) // 图片稍微大一点显得更霸气
                                .padding(8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "FakeFluent",
                            color = Color.White,
                            fontSize = 40.sp, // 字号加大一点
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Coach",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 20.sp
                        )
                    }
                }

                // 跳转逻辑
                LaunchedEffect(Unit) {
                    delay(2000)
                    val intent = Intent(this@SplashActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}