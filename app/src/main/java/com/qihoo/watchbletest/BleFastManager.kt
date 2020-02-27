package com.qihoo.watchbletest

import android.app.Application
import android.bluetooth.BluetoothGatt
import com.clj.fastble.BleManager
import com.clj.fastble.callback.*
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import java.util.*

/**
 * @author yolo.huang
 * @date 2019-10-31
 */
object BleFastManager {


    var mBleScanListener: BleScanListener? = null

    var mBleConnectListener: BleConnectListener? = null

    var mBleNotifyListener: BleNotifyListener? = null

    var mBleWriteListener: BleWriteListener? = null

    var macAddress: String = "BleFastManager"

    var bleDevice: BleDevice? = null

    var gatt: BluetoothGatt? = null

    var phoneNumber: String? = null

    var activationCode: String? = null


    val UUID_SERVER = UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb")
    val UUID_CHARREAD = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb")
    val CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val UUID_CHARWRITE = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    val UUID_ADV_SERVER = UUID.fromString("0000ffe3-0000-1000-8000-00805f9b34fb")

    val uuid = arrayOf(UUID_SERVER)


    fun initBle(app: Application) {
        BleManager.getInstance().init(app)
        BleManager.getInstance().enableLog(true)
        val scanRuleConfig: BleScanRuleConfig =
            BleScanRuleConfig.Builder().setDeviceName(true, macAddress)
                .setScanTimeOut(5000).build()
        BleManager.getInstance().initScanRule(scanRuleConfig)
    }

    fun scan() {
        BleManager.getInstance().scan(object : BleScanCallback() {
            override fun onScanFinished(scanResultList: MutableList<BleDevice>?) {
                mBleScanListener?.onScanFinish(scanResultList)
            }

            override fun onScanStarted(success: Boolean) {
                mBleScanListener?.onScanStarted(success)
            }

            override fun onScanning(bleDevice: BleDevice?) {
                mBleScanListener?.onScaning(bleDevice)
            }
        })
    }

    fun connect() {
        BleManager.getInstance().connect(bleDevice, object : BleGattCallback() {
            override fun onStartConnect() {
                mBleConnectListener?.onStartConnect()
            }

            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                device: BleDevice?,
                gatt: BluetoothGatt?,
                status: Int
            ) {
                BleFastManager.gatt = gatt
                mBleConnectListener?.onDisConnected(isActiveDisConnected, device, gatt, status)
            }

            override fun onConnectSuccess(
                bleDevice: BleDevice?,
                gatt: BluetoothGatt?,
                status: Int
            ) {
                BleFastManager.gatt = gatt
                //BleFastManager.gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                mBleConnectListener?.onConnectSuccess(bleDevice, gatt, status)
            }

            override fun onConnectFail(bleDevice: BleDevice?, exception: BleException?) {
                mBleConnectListener?.onConnectFail(bleDevice, exception)
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                mBleConnectListener?.onConnectionStateChange(gatt, status, newState)
            }
        })
    }

    fun write(data: ByteArray) {
        BleManager.getInstance().write(
            bleDevice,
            UUID_SERVER.toString(),
            UUID_CHARWRITE.toString(),
            data,
            object : BleWriteCallback() {
                override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray) {
                    mBleWriteListener?.writeSuccess(current, total, justWrite)
                }

                override fun onWriteFailure(exception: BleException?) {
                    mBleWriteListener?.writeFailed(exception)
                }

            })
    }

    fun notifyBle(){
        BleManager.getInstance().notify(bleDevice, UUID_SERVER.toString(),
            UUID_CHARWRITE.toString(),object :BleNotifyCallback(){
            override fun onCharacteristicChanged(data: ByteArray) {
                mBleNotifyListener?.onCharacteristicChanged(data)
            }

            override fun onNotifyFailure(exception: BleException?) {
                mBleNotifyListener?.onNotifyFailure(exception)
            }

            override fun onNotifySuccess() {
                mBleNotifyListener?.onNotifySuccess()
            }
        })
    }

    interface BleScanListener {
        fun onScaning(bleDevice: BleDevice?)
        fun onScanStarted(success: Boolean)
        fun onScanFinish(scanResultList: MutableList<BleDevice>?)
    }

    interface BleConnectListener {

        fun onStartConnect()
        fun onDisConnected(
            isActiveDisConnected: Boolean,
            device: BleDevice?,
            gatt: BluetoothGatt?,
            status: Int
        )

        fun onConnectSuccess(
            bleDevice: BleDevice?,
            gatt: BluetoothGatt?,
            status: Int
        )

        fun onConnectFail(bleDevice: BleDevice?, exception: BleException?)
        fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int)
    }

    interface BleWriteListener {
        fun writeSuccess(current: Int, total: Int, justWrite: ByteArray)
        fun writeFailed(exception: BleException?)
    }

    interface BleNotifyListener {
        fun onCharacteristicChanged(data: ByteArray)
        fun onNotifySuccess()
        fun onNotifyFailure(exception: BleException?)
    }

}