package com.bharathvishal.messagecommunicationusingwearabledatalayer

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bharathvishal.messagecommunicationusingwearabledatalayer.databinding.ActivityMainBinding
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleGattCallback
import com.clj.fastble.callback.BleNotifyCallback
import com.clj.fastble.callback.BleReadCallback
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.utils.HexUtil
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity(), CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {
    var activityContext: Context? = null
    private val wearableAppCheckPayload = "AppOpenWearable"
    private val wearableAppCheckPayloadReturnACK = "AppOpenWearableACK"
    private var wearableDeviceConnected: Boolean = false

    private var currentAckFromWearForAppOpenCheck: String? = null
    private val APP_OPEN_WEARABLE_PAYLOAD_PATH = "/APP_OPEN_WEARABLE_PAYLOAD"

    private val MESSAGE_ITEM_RECEIVED_PATH: String = "/message-item-received"

    private val TAG_GET_NODES: String = "getnodes1"
    private val TAG_MESSAGE_RECEIVED: String = "receive1"

    private var messageEvent: MessageEvent? = null
    private var wearableNodeUri: String? = null

    private lateinit var binding: ActivityMainBinding

    private var targetDevice:BleDevice?=null
    private val handler = Handler(Looper.getMainLooper())
    private val tServiceUuid= UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val tCharUuid=UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    val executor = Executors.newFixedThreadPool(1)
    var tag="MainActivity"
    lateinit var ubr:UsbBridge
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        BleManager.getInstance().init(application)
        BleManager.getInstance()
            .enableLog(true)
            .setReConnectCount(1, 5000)
            .setConnectOverTime(20000).operateTimeout = 5000

        activityContext = this
        wearableDeviceConnected = false
        binding.sbtn.setOnClickListener { startScan() }
        binding.cbtn.setOnClickListener {mConnect()}

        binding.rbtn.setOnClickListener {mRead()}

        binding.nbtn.setOnClickListener { mNotify() }

        binding.ebtn.setOnClickListener { exeRead() }
        binding.checkwearablesButton.setOnClickListener {
            if (!wearableDeviceConnected) {
                val tempAct: Activity = activityContext as MainActivity
                //Couroutine
                initialiseDevicePairing(tempAct)
            }
        }



        binding.sendmessageButton.setOnClickListener {
            if (wearableDeviceConnected) {
                if (binding.messagecontentEditText.text!!.isNotEmpty()) {

                    val nodeId: String = messageEvent?.sourceNodeId!!
                    Log.d("nodeid",nodeId)
                    // Set the data of the message to be the bytes of the Uri.
                    val payload: ByteArray =
                        binding.messagecontentEditText.text.toString().toByteArray()

                    // Send the rpc
                    // Instantiates clients without member variables, as clients are inexpensive to
                    // create. (They are cached and shared between GoogleApi instances.)
                    val sendMessageTask =
                        Wearable.getMessageClient(activityContext!!)
                            .sendMessage(nodeId, MESSAGE_ITEM_RECEIVED_PATH, payload)

                    sendMessageTask.addOnCompleteListener {
                        if (it.isSuccessful) {
                            Log.d("send1", "Message sent successfully")
                            val sbTemp = StringBuilder()
                            sbTemp.append("\n")
                            sbTemp.append(binding.messagecontentEditText.text.toString())
                            sbTemp.append(" (Sent to Wearable)")
                            Log.d("receive1", " $sbTemp")
                            binding.messagelogTextView.append(sbTemp)

                            binding.scrollviewText.requestFocus()
                            binding.scrollviewText.post {
                                binding.scrollviewText.scrollTo(0, binding.scrollviewText.bottom)
                            }
                        } else {
                            Log.d("send1", "Message failed.")
                        }
                    }
                } else {
                    Toast.makeText(
                        activityContext,
                        "Message content is empty. Please enter some message and proceed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        binding.buttonServiceStart.setOnClickListener {

            val intent = Intent(this,ForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            }
        }


        binding.buttonServiceClose.setOnClickListener {

            val intent = Intent(this,ForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                stopService(intent)
            }
        }
    }

    fun mNotify(){

        executor.execute(Runnable {

            BleManager.getInstance().notify(
                targetDevice,
                tServiceUuid.toString(),
                tCharUuid.toString(),
                object : BleNotifyCallback() {
                    override fun onNotifySuccess() {

                        sendToWear("notify success")
                        Log.d(tag,"notify success")

                    }
                    override fun onNotifyFailure(exception: BleException) {}
                    override fun onCharacteristicChanged(data: ByteArray) {


                        sendToWear(HexUtil.formatHexString(data, true))
                        Log.d(tag,HexUtil.formatHexString(data, true))
                    }
                })
        })
        }

    fun mRead(){
        val gatt = BleManager.getInstance().getBluetoothGatt(targetDevice)

        var showtxt:String=":";
        for (service in gatt.services) {
            Log.d(tag,service.uuid.toString())
            showtxt+=service.uuid.toString()
            if(service.uuid.toString()==tServiceUuid.toString()){
                Log.d(tag,"service found")
                for (characteristic in service.characteristics) {
                    Log.d(tag,characteristic.uuid.toString())
                    if(characteristic.uuid.toString()==tCharUuid.toString()){
                        BleManager.getInstance().read(
                            targetDevice,
                            tServiceUuid.toString(),
                            tCharUuid.toString(),
                            object : BleReadCallback() {
                                override fun onReadSuccess(data: ByteArray) {

                                    handler.post(){

                                        binding.mtext.setText("val:"+HexUtil.formatHexString(data, true))
                                    }
                                }
                                override fun onReadFailure(exception: BleException) {

                                    handler.post(){

                                        binding.mtext.setText("read failed")
                                    }
                                }
                            })
                    }
                }
            }

        }
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
                        handler.post(){
                            binding.mtext.setText("target found")
                        }
                    }
                }
            }

            override fun onScanFinished(scanResultList: List<BleDevice>) {

            }
        })
    }

    fun mConnect(){
        BleManager.getInstance().connect(targetDevice, object : BleGattCallback() {
            override fun onStartConnect() {}
            override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {}
            override fun onConnectSuccess(bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {

                handler.post(){

                    binding.mtext.setText("connect success")
                }
            }
            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                bleDevice: BleDevice,
                gatt: BluetoothGatt,
                status: Int
            ) {
            }
        })
    }

    fun exeRead(){
        executor.execute(Runnable {

            while (true) {
                Log.d(tag, "I will log this line every 10 seconds forever")
                Thread.sleep(2000);
                BleManager.getInstance().read(
                    targetDevice,
                    tServiceUuid.toString(),
                    tCharUuid.toString(),
                    object : BleReadCallback() {
                        override fun onReadSuccess(data: ByteArray) {



                            Log.d(tag,"val:"+HexUtil.formatHexString(data, true))

                            sendToWear(HexUtil.formatHexString(data, true))

                        }
                        override fun onReadFailure(exception: BleException) {



                            Log.d(tag,"read failed")

                        }
                    })

            }
        })
    }

    fun sendToWear(mes:String){
        try {
            //val mes="a"
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
    @SuppressLint("SetTextI18n")
    private fun initialiseDevicePairing(tempAct: Activity) {
        //Coroutine
        launch(Dispatchers.Default) {
            var getNodesResBool: BooleanArray? = null

            try {
                getNodesResBool =
                    getNodes(tempAct.applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            //UI Thread
            withContext(Dispatchers.Main) {
                if (getNodesResBool!![0]) {
                    //if message Acknowlegement Received
                    if (getNodesResBool[1]) {
                        Toast.makeText(
                            activityContext,
                            "Wearable device paired and app is open. Tap the \"Send Message to Wearable\" button to send the message to your wearable device.",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.deviceconnectionStatusTv.text =
                            "Wearable device paired and app is open."
                        binding.deviceconnectionStatusTv.visibility = View.VISIBLE
                        wearableDeviceConnected = true
                        binding.sendmessageButton.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(
                            activityContext,
                            "A wearable device is paired but the wearable app on your watch isn't open. Launch the wearable app and try again.",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.deviceconnectionStatusTv.text =
                            "Wearable device paired but app isn't open."
                        binding.deviceconnectionStatusTv.visibility = View.VISIBLE
                        wearableDeviceConnected = false
                        binding.sendmessageButton.visibility = View.GONE
                    }
                } else {
                    Toast.makeText(
                        activityContext,
                        "No wearable device paired. Pair a wearable device to your phone using the Wear OS app and try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.deviceconnectionStatusTv.text =
                        "Wearable device not paired and connected."
                    binding.deviceconnectionStatusTv.visibility = View.VISIBLE
                    wearableDeviceConnected = false
                    binding.sendmessageButton.visibility = View.GONE
                }
            }
        }
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


    override fun onDataChanged(p0: DataEventBuffer) {
    }

    @SuppressLint("SetTextI18n")
    override fun onMessageReceived(p0: MessageEvent) {
        try {
            val s =
                String(p0.data, StandardCharsets.UTF_8)
            val messageEventPath: String = p0.path
            Log.d(
                TAG_MESSAGE_RECEIVED,
                "onMessageReceived() Received a message from watch:"
                        + p0.requestId
                        + " "
                        + messageEventPath
                        + " "
                        + s
            )
            if (messageEventPath == APP_OPEN_WEARABLE_PAYLOAD_PATH) {
                currentAckFromWearForAppOpenCheck = s
                Log.d(
                    TAG_MESSAGE_RECEIVED,
                    "Received acknowledgement message that app is open in wear"
                )

                val sbTemp = StringBuilder()
                sbTemp.append(binding.messagelogTextView.text.toString())
                sbTemp.append("\nWearable device connected.")
                Log.d("receive1", " $sbTemp")
                binding.messagelogTextView.text = sbTemp
                binding.textInputLayout.visibility = View.VISIBLE

                binding.checkwearablesButton.visibility = View.GONE
                messageEvent = p0
                wearableNodeUri = p0.sourceNodeId
            } else if (messageEventPath.isNotEmpty() && messageEventPath == MESSAGE_ITEM_RECEIVED_PATH) {

                try {
                    binding.messagelogTextView.visibility = View.VISIBLE
                    binding.textInputLayout.visibility = View.VISIBLE
                    binding.sendmessageButton.visibility = View.VISIBLE

                    val sbTemp = StringBuilder()
                    sbTemp.append("\n")
                    sbTemp.append(s)
                    sbTemp.append(" - (Received from wearable)")
                    Log.d("receive1", " $sbTemp")
                    binding.messagelogTextView.append(sbTemp)

                    binding.scrollviewText.requestFocus()
                    binding.scrollviewText.post {
                        binding.scrollviewText.scrollTo(0, binding.scrollviewText.bottom)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("receive1", "Handled")
        }
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
    }


    override fun onPause() {
        super.onPause()
        try {
            Wearable.getDataClient(activityContext!!).removeListener(this)
            Wearable.getMessageClient(activityContext!!).removeListener(this)
            Wearable.getCapabilityClient(activityContext!!).removeListener(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    override fun onResume() {
        super.onResume()
        try {
            Wearable.getDataClient(activityContext!!).addListener(this)
            Wearable.getMessageClient(activityContext!!).addListener(this)
            Wearable.getCapabilityClient(activityContext!!)
                .addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun readUsb() {
        try {
            binding.mtext.setText(ubr.readUsb())
        }catch (e:Exception){
            binding.mtext.setText(e.toString())
        }
    }
    fun conUsb() {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList: HashMap<String, UsbDevice> = manager.deviceList
        deviceList.values.forEach { device ->
            //your code
            Log.d(tag, "dev")
        }

        ubr= UsbBridge(manager)
        binding.mtext.setText(ubr.logtxt)
    }
}
