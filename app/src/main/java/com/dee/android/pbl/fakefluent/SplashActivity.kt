package com.dee.android.pbl.fakefluent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // 1. 渐变背景层
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF2196F3), // 亮蓝色
                                    Color(0xFF0D47A1)  // 深蓝色
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 2. Logo 图标 (加入了 clip 确保圆形边缘完美)
                        Image(
                            painter = painterResource(id = R.drawable.splash_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(180.dp)
                                .clip(CircleShape) // 即使你抠了图，这行能保证边缘过度更自然
                                .padding(8.dp),
                            contentScale = ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // 3. App 名字
                        Text(
                            text = "FakeFluent",
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        )

                        // 4. 副标题
                        Text(
                            text = "AI English Coach", // 稍微改得更有科技感一点
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // 5. 跳转逻辑：保持 2 秒展示后进入主界面
                LaunchedEffect(key1 = Unit) {
                    delay(2000)
                    val intent = Intent(this@SplashActivity, MainActivity::class.java)
                    startActivity(intent)
                    // 渐变动画进入下一页（可选）
                    finish()
                }
            }
        }
    }
}