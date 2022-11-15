package com.bharathvishal.messagecommunicationusingwearabledatalayer


import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import java.lang.Runnable
import java.util.*

import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleGattCallback
import com.clj.fastble.callback.BleReadCallback
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.utils.HexUtil

class ForegroundService: Service(), CoroutineScope by MainScope() {

    private val wearableAppCheckPayload = "AppOpenWearable"
    private val wearableAppCheckPayloadReturnACK = "AppOpenWearableACK"


    private var currentAckFromWearForAppOpenCheck: String? = null
    private val APP_OPEN_WEARABLE_PAYLOAD_PATH = "/APP_OPEN_WEARABLE_PAYLOAD"
    private val MESSAGE_ITEM_RECEIVED_PATH: String = "/message-item-received"

    private val TAG_GET_NODES: String = "getnodes1"
    private var wearableDeviceConnected: Boolean = false
    private var messageEvent: MessageEvent? = null

    var tag="fgs"
    //private var mDeviceAdapter: DeviceAdapter? = null
    private var targetDevice:BleDevice?=null
    private val tServiceUuid= UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val tCharUuid= UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
   // lateinit var ubr:UsbBridge

    companion object {
        const val CHANNEL_ID = "1111"
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("Service", "onStartCommand called")

      //  conUsb()
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
        initialiseDevicePairing(this)

        Log.d(tag,"bef scan")
        startScan()
        Thread(
            Runnable {
                while(true) {
                    Thread.sleep(1000)
              //      Log.d("FGS","working")

                    try {
val mes="a"
                        val payload: ByteArray =
                            mes.toByteArray()

                        Log.d("nodeid", "bef")

                        //    val nodeId: String = messageEvent?.sourceNodeId!!
                        val nodeId: String = "17e215e4"

                        nodeId?.let { hoge ->
                            // hogeがnullでないときだけ実行
                            Log.d("nodeid", nodeId)

                        }
                        // Send the rpc
                        // Instantiates clients without member variables, as clients are inexpensive to
                        // create. (They are cached and shared between GoogleApi instances.)
                        val sendMessageTask =
                            Wearable.getMessageClient(this)
                                .sendMessage(nodeId, MESSAGE_ITEM_RECEIVED_PATH, payload)

                        sendMessageTask.addOnCompleteListener {
                            if (it.isSuccessful) {
                                Log.d("send1", "Message sent successfully")

                            } else {
                                Log.d("send1", "Message failed.")
                            }
                        }
                    }catch (e: Exception){
                        Log.d("FG",e.toString());
                    }

                }

            }).start()

        //5．フォアグラウンド開始。
        startForeground(2222, notification)

        return START_STICKY
    }
    private fun startScan() {
        BleManager.getInstance().scan(object : BleScanCallback() {
            override fun onScanStarted(success: Boolean) {
                // mDeviceAdapter?.clearScanDevice()
                //    mDeviceAdapter?.notifyDataSetChanged()

            }

            override fun onLeScan(bleDevice: BleDevice) {
                super.onLeScan(bleDevice)
            }

            override fun onScanning(bleDevice: BleDevice) {
                // mDeviceAdapter?.addDevice(bleDevice)
                //  mDeviceAdapter?.notifyDataSetChanged()
                if (bleDevice.name != null) {
                    Log.d(tag, bleDevice.name)
                    if(bleDevice.name.contains("UART")){
                        targetDevice=bleDevice
                        BleManager.getInstance().cancelScan();
                        Log.d(tag,"t found")
                    }
                }
            }

            override fun onScanFinished(scanResultList: List<BleDevice>) {

            }
        })
    }
    override fun stopService(name: Intent?): Boolean {
        return super.stopService(name)
    }

    private fun initialiseDevicePairing(tempCon: Context) {
        //Coroutine
        launch(Dispatchers.Default) {
            var getNodesResBool: BooleanArray? = null

            try {
                getNodesResBool =
                    getNodes(tempCon)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            //UI Thread
            withContext(Dispatchers.Main) {
                if (getNodesResBool!![0]) {
                    //if message Acknowlegement Received
                    wearableDeviceConnected = getNodesResBool[1]
                } else {
                            wearableDeviceConnected = false
                         }
            }
        }
    }
    fun conUsb() {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList: HashMap<String, UsbDevice> = manager.deviceList
        deviceList.values.forEach { device ->
            //your code
            Log.d("fgs", "dev")
        }

     //   ubr= UsbBridge(manager)

    }
    private fun getNodes(context: Context): BooleanArray {
        val nodeResults = HashSet<String>()
        val resBool = BooleanArray(2)
        resBool[0] = false //nodePresent
        resBool[1] = false //wearableReturnAckReceived
        val nodeListTask =
            Wearable.getNodeClient(context).connectedNodes
        try {
            // Block on a task and get the result synchronously (because this is on a background thread).
            val nodes =
                Tasks.await(
                    nodeListTask
                )
            Log.e(TAG_GET_NODES, "Task fetched nodes")
            for (node in nodes) {
                Log.e(TAG_GET_NODES, "inside loop")
                nodeResults.add(node.id)
                try {
                    val nodeId = node.id
                    // Set the data of the message to be the bytes of the Uri.
                    val payload: ByteArray = wearableAppCheckPayload.toByteArray()
                    // Send the rpc
                    // Instantiates clients without member variables, as clients are inexpensive to
                    // create. (They are cached and shared between GoogleApi instances.)
                    val sendMessageTask =
                        Wearable.getMessageClient(context)
                            .sendMessage(nodeId, APP_OPEN_WEARABLE_PAYLOAD_PATH, payload)
                    try {
                        // Block on a task and get the result synchronously (because this is on a background thread).
                        val result = Tasks.await(sendMessageTask)
                        Log.d(TAG_GET_NODES, "send message result : $result")
                        resBool[0] = true
                        //Wait for 1000 ms/1 sec for the acknowledgement message
                        //Wait 1
                        if (currentAckFromWearForAppOpenCheck != wearableAppCheckPayloadReturnACK) {
                            Thread.sleep(100)
                            Log.d(TAG_GET_NODES, "ACK thread sleep 1")
                        }
                        if (currentAckFromWearForAppOpenCheck == wearableAppCheckPayloadReturnACK) {
                            resBool[1] = true
                            return resBool
                        }
                        //Wait 2
                        if (currentAckFromWearForAppOpenCheck != wearableAppCheckPayloadReturnACK) {
                            Thread.sleep(150)
                            Log.d(TAG_GET_NODES, "ACK thread sleep 2")
                        }
                        if (currentAckFromWearForAppOpenCheck == wearableAppCheckPayloadReturnACK) {
                            resBool[1] = true
                            return resBool
                        }
                        //Wait 3
                        if (currentAckFromWearForAppOpenCheck != wearableAppCheckPayloadReturnACK) {
                            Thread.sleep(200)
                            Log.d(TAG_GET_NODES, "ACK thread sleep 3")
                        }
                        if (currentAckFromWearForAppOpenCheck == wearableAppCheckPayloadReturnACK) {
                            resBool[1] = true
                            return resBool
                        }
                        //Wait 4
                        if (currentAckFromWearForAppOpenCheck != wearableAppCheckPayloadReturnACK) {
                            Thread.sleep(250)
                            Log.d(TAG_GET_NODES, "ACK thread sleep 4")
                        }
                        if (currentAckFromWearForAppOpenCheck == wearableAppCheckPayloadReturnACK) {
                            resBool[1] = true
                            return resBool
                        }
                        //Wait 5
                        if (currentAckFromWearForAppOpenCheck != wearableAppCheckPayloadReturnACK) {
                            Thread.sleep(350)
                            Log.d(TAG_GET_NODES, "ACK thread sleep 5")
                        }
                        if (currentAckFromWearForAppOpenCheck == wearableAppCheckPayloadReturnACK) {
                            resBool[1] = true
                            return resBool
                        }
                        resBool[1] = false
                        Log.d(
                            TAG_GET_NODES,
                            "ACK thread timeout, no message received from the wearable "
                        )
                    } catch (exception: Exception) {
                        exception.printStackTrace()
                    }
                } catch (e1: Exception) {
                    Log.d(TAG_GET_NODES, "send message exception")
                    e1.printStackTrace()
                }
            } //end of for loop
        } catch (exception: Exception) {
            Log.e(TAG_GET_NODES, "Task failed: $exception")
            exception.printStackTrace()
        }
        return resBool
    }



}