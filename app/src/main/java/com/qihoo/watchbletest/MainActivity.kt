package com.qihoo.watchbletest

import android.bluetooth.BluetoothGatt
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {


    var watchList: MutableList<BleDevice>? = null
    /**
     * 0开始状态
     * 1表示扫描完成
     * 2表示传输数据
     */
    var status: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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
                0 -> BleFastManager.scan()
                1 -> {
                    BleFastManager.bleDevice = watchList?.get(0)
                    BleFastManager.connect()
                }
                2 -> {
                    //发送停止指令
                    BleFastManager.write(byteArrayOf(2))
                    reset()
                }
            }
        }
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

                }


            }
        }
        BleFastManager.mBleNotifyListener =
            object : BleFastManager.BleNotifyListener {
                override fun onCharacteristicChanged(data: ByteArray) {
                    //接收数据
                    Log.e("xulinchao", "onCharacteristicChanged")

                }

                override fun onNotifySuccess() {
                    //通知成功后，开始发送指令,比如开始和停止指令
                    Log.e("xulinchao", "onNotifySuccess")
                    status = 2
                    ble_tip.text = "开始传输数据"
                    ble_operation.text = "停止"
                    BleFastManager.write(byteArrayOf(1))

                }

                override fun onNotifyFailure(exception: BleException?) {
                }
            }
        BleFastManager.mBleWriteListener =
            object : BleFastManager.BleWriteListener {
                override fun writeSuccess(current: Int, total: Int, justWrite: ByteArray) {
                    //这边检测是否写入成功，成功后，起alarm定时10min，给服务器发送数据
                    Log.e("xulinchao", "writeSuccess")

                }

                override fun writeFailed(exception: BleException?) {
                    //写入失败后，停止发送数据
                    Log.e("xulinchao", "writeFailed")
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
                    if (newState == 0) {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.ble_connect_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                        reset()
                    }
                }

            }
    }

    fun reset() {
        ble_tip.text = "开始测试"
        ble_operation.text = "扫描"
        status = 0
    }
}