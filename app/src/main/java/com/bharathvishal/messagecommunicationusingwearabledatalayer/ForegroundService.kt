package com.bharathvishal.messagecommunicationusingwearabledatalayer


import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat


class ForegroundService: Service() {
    companion object {
        const val CHANNEL_ID = "1111"
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("Service", "onStartCommand called")

        //1．通知領域タップで戻ってくる先のActivity
        val openIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        //2．通知チャネル登録
        val channelId = CHANNEL_ID
        val channelName = "TestService Channel"
        val channel = NotificationChannel(
            channelId, channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        //3．ブロードキャストレシーバーをPendingIntent化
        val sendIntent = Intent(this, ForegroundReceiver::class.java).apply {
            action = Intent.ACTION_SEND
        }


        val sendPendingIntent = PendingIntent.getBroadcast(this, 0, sendIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        //4．通知の作成（ここでPendingIntentを通知領域に渡す）
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("フォアグラウンドのテスト中")
            .setContentText("終了する場合はこちらから行って下さい。")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_launcher_foreground, "実行終了", sendPendingIntent)
            .build()

        //5．フォアグラウンド開始。
        startForeground(2222, notification)

        return START_STICKY
    }

    override fun stopService(name: Intent?): Boolean {
        return super.stopService(name)
    }


}