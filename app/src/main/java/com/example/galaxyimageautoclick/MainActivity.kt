package com.example.galaxyautoclick

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("config", MODE_PRIVATE) }
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 35, 30, 20) }
        fun button(label: String, click: () -> Unit) { root.addView(Button(this).apply { text = label; setOnClickListener { click() } }) }
        root.addView(TextView(this).apply { text = "이미지 자동클릭 (갤럭시 / Android 11+)\n화면 캡처에서 버튼 부분을 잘라 이미지로 저장한 뒤 선택하세요.\n다른 앱을 열기 전에 시작을 누르세요."; textSize = 17f })
        status = TextView(this).apply { text = "상태: 대기" }; root.addView(status)
        button("1. 클릭할 이미지 선택") { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, 10) }
        button("2. 접근성 서비스 설정 열기") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        val seconds = EditText(this).apply { inputType = 2; setText(prefs.getInt("interval", 2).toString()); hint = "검색 간격(초): 1~60" }
        root.addView(seconds)
        button("3. 시작") {
            val n = (seconds.text.toString().toIntOrNull() ?: 2).coerceIn(1, 60)
            prefs.edit().putInt("interval", n).putBoolean("running", true).apply()
            ClickService.instance?.startLoop()
            status.text = if (ClickService.instance == null) "접근성 서비스를 먼저 켜주세요" else "실행 중. 다른 앱으로 이동하세요."
        }
        button("정지") { prefs.edit().putBoolean("running", false).apply(); ClickService.instance?.stopLoop(); status.text = "정지됨" }
        root.addView(TextView(this).apply { text = "주의: 자동 클릭은 화면이 켜져 있고 접근성 서비스가 활성화된 동안만 동작합니다. 앱에서 정지를 누르거나 접근성 서비스를 끄면 중지됩니다." })
        setContentView(ScrollView(this).apply { addView(root) })
    }
    @Deprecated("Uses activity result for a minimal starter project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10 && resultCode == RESULT_OK) {
            try {
                val uri = data?.data ?: return
                val bitmap = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                require(bitmap != null && bitmap.width >= 10 && bitmap.height >= 10) { "이미지가 너무 작거나 읽을 수 없습니다" }
                File(filesDir, "template.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                status.text = "이미지 등록 완료 (${bitmap.width}x${bitmap.height})"
            } catch (e: Exception) { status.text = "이미지 오류: ${e.message}" }
        }
    }
}
