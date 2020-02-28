package com.qihoo.watchbletest

import android.bluetooth.BluetoothGatt
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.google.gson.JsonObject
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.lang.StringBuilder
import java.util.concurrent.*


class MainActivity : AppCompatActivity() {
    var isWriteSuccess = true
    var handler: Handler = Handler(Handler.Callback {
        if (it.what == 1) {
            postRequest()
        }
        when (it.what) {
            1 -> postRequest()
            2 -> {
                //25s后,发送开始指令
                if (isWriteSuccess) {
                    BleFastManager.write(byteArrayOf(1))
                    sendWrite()
                }
            }
        }
        false
    })
    var watchList: MutableList<BleDevice>? = null
    private val okHttpClient: OkHttpClient = OkHttpClient()
    private var stringBuilder: StringBuilder = StringBuilder()
    private var isFirstConnect = true
    private val mSingleThreadPool: ExecutorService = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(1024),
        Executors.defaultThreadFactory(),
        ThreadPoolExecutor.AbortPolicy()
    )

    /**
     * 0开始状态
     * 1表示扫描完成
     * 2表示传输数据
     */
    var status: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        //发生断连问题,这个还不清楚，是否需要断连策略
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkCallingPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                init()
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
            }
        } else {
            init()
        }

    }

    fun init() {
        initView()
        initData()
    }
    private fun disconnect(){
        BleFastManager.gatt?.disconnect()
        BleFastManager.gatt?.close()
    }
    private fun sendWrite() {
        handler.sendEmptyMessageDelayed(2, 25 * 1000)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            init()
        } else {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initView() {
        ble_operation.setOnClickListener {
            when (status) {
                0 -> {
                    BleFastManager.scan()
                }
                1 -> {
                    connectDevice()
                }
                2 -> {
                    //发送停止指令
                    BleFastManager.write(byteArrayOf(2))
                    reset()
                }
            }
        }
        ble_network.setOnClickListener { postRequest() }
    }

    fun connectDevice() {
        BleFastManager.bleDevice = watchList?.get(0)
        BleFastManager.connect()
    }

    private fun initData() {
        initBle()
    }


    /**
     * 初始化BLE 蓝牙
     */
    private fun initBle() {
        BleFastManager.initBle(application)
        BleFastManager.mBleScanListener = object : BleFastManager.BleScanListener {
            override fun onScaning(bleDevice: BleDevice?) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.ble_search_doing),
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onScanStarted(success: Boolean) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.ble_search_start),
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onScanFinish(scanResultList: MutableList<BleDevice>?) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.ble_search_end),
                    Toast.LENGTH_SHORT
                ).show()
                if (scanResultList.isNullOrEmpty()) {
                    status = 0
                    ble_tip.text = "未搜索到蓝牙设备，请尝试继续搜索"
                    ble_operation.text = "搜索"
                } else {
                    watchList = scanResultList
                    status = 1
                    ble_tip.text = "寻找到蓝牙设备,请点击开始进行连接和数据传输"
                    ble_operation.text = "开始"
                    if (!isWriteSuccess) {
                        connectDevice()
                    }

                }


            }
        }
        BleFastManager.mBleNotifyListener =
            object : BleFastManager.BleNotifyListener {
                override fun onCharacteristicChanged(data: ByteArray) {
                    //接收数据
                    //Log.d("xulinchao","$data")
                    //stringBuilder.append(data)

                }

                override fun onNotifySuccess() {
                    //通知成功后，开始发送指令,比如开始和停止指令
                    Log.e("xulinchao", "onNotifySuccess")
                    if (isFirstConnect) {
                        //handler.sendEmptyMessageDelayed(1, POST_TIME)
                        status = 2
                        ble_tip.text = "开始传输数据"
                        ble_operation.text = "停止"
                    }
                    BleFastManager.write(byteArrayOf(1))
                    handler.sendEmptyMessageDelayed(2, 25 * 1000)
                }

                override fun onNotifyFailure(exception: BleException?) {
                }
            }
        BleFastManager.mBleWriteListener =
            object : BleFastManager.BleWriteListener {
                override fun writeSuccess(current: Int, total: Int, justWrite: ByteArray) {
                    //这边检测是否写入成功，成功后，起alarm定时10min，给服务器发送数据
                    Log.e("xulinchao", "writeSuccess")
                    isWriteSuccess = true

                }

                override fun writeFailed(exception: BleException?) {
                    //写入失败后，停止发送数据
                    Log.e("xulinchao", "writeFailed")
                    isWriteSuccess = false
                    //3s后再去连接
                    BleFastManager.gatt?.disconnect()
                    BleFastManager.gatt?.close()
                    handler.postDelayed({ BleFastManager.scan() }, 3 * 1000)
                }

            }
        BleFastManager.mBleConnectListener =
            object : BleFastManager.BleConnectListener {
                override fun onStartConnect() {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.ble_connect_start),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onDisConnected(
                    isActiveDisConnected: Boolean,
                    device: BleDevice?,
                    gatt: BluetoothGatt?,
                    status: Int
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.ble_disconnect),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onConnectSuccess(
                    bleDevice: BleDevice?,
                    gatt: BluetoothGatt?,
                    status: Int
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.ble_connect_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    BleFastManager.notifyBle()
                }

                override fun onConnectFail(bleDevice: BleDevice?, exception: BleException?) {
                    Log.e("MainActivity", "onConnectFail")

                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.ble_connect_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    reset()
                }

                override fun onConnectionStateChange(
                    gatt: BluetoothGatt?,
                    status: Int,
                    newState: Int
                ) {
                    Log.e("xulinchao", "发生了变化")
                    if (newState == 0) {
                        isFirstConnect = false
                        Log.e("xulinchao", "发生了断连,开始自动连接")
                        connectDevice()
                    }
                }

            }
    }

    private fun postRequest() {
        val jsonObject = JsonObject()
        jsonObject.addProperty("str", stringBuilder.toString())
        val body: RequestBody = RequestBody.create(MEDIA_TYPE_MARKDOWN, jsonObject.toString())
        val request = Request.Builder()
            .url("http://devapp.artimen.cn:8001/Service/LogService.asmx/uploadStr")
            .post(body)
            .build()
        mSingleThreadPool.execute {
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.e("xulinchao", "successful body = " + response.body()?.string())
                    } else {
                        Log.e("xulinchao", "failed")
                    }

                }
            } catch (e: Exception) {
                Log.e("xulinchao", e.printStackTrace().toString())
            }
            Log.e("xulinchao", "$stringBuilder")
            //继续延迟10min，然后进行发送任务,记得连wifi
            stringBuilder.clear()
            handler.sendEmptyMessageDelayed(1, POST_TIME)
        }
    }

    companion object {
        val MEDIA_TYPE_MARKDOWN = MediaType.parse("application/json; charset=utf-8")
        var POST_TIME = 1 * 60 * 1000L
    }

    override fun onDestroy() {
        super.onDestroy()
        mSingleThreadPool.shutdown()
    }

    fun reset() {
        ble_tip.text = "开始测试"
        ble_operation.text = "扫描"
        status = 0
    }
}


