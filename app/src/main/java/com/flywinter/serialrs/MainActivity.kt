package com.flywinter.serialrs

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.*
import android.text.method.DigitsKeyListener
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.nio.charset.Charset
import java.util.*
import kotlin.concurrent.thread

/**
 * @author Zhang Xingkun
 * @note Android串口收发程序
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val READ_WAIT_MILLIS = 2000
        private const val WRITE_WAIT_MILLIS = 2000
        private const val TAG = "MainActivity"
        private const val HANDLER_RECEIVE_BUNDLE = "serialReceive"
        private const val HANDLER_RECEIVE_MESSAGE_WHAT = 124
    }

    private lateinit var serialDriver: UsbSerialDriver
    private lateinit var serialPort: UsbSerialPort
    private var availableDrivers = mutableListOf<UsbSerialDriver>()
    private var serialSendMessage = String()
    private var serialConnected = false
    private var encodingFormat = "GBK"
    private var stringBuffer = StringBuffer()



    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //配合xml布局实现text滚动效果
        txt_serial_receive.movementMethod = ScrollingMovementMethod.getInstance()
        //txt_serial_info.append("f11".toInt(16).toString())
        //字符串转16进制思路，首先转为Unicode格式，然后转为16进制
        //获取默认属性
        //val keyListener = edit_serial_send.keyListener
        //打开或关闭串口连接
        switch_serial_status.setOnClickListener {
            if (switch_serial_status.isChecked && funSerialConnect()) {
                switch_serial_status.isChecked = true
                serialConnected = true
                thread {
                    //开启串口接收
                    funSerialReceive()
                }
            } else {
                switch_serial_status.isChecked = false
                //判断变量是否初始化
                if (this::serialPort.isInitialized && serialPort.isOpen) {
                    serialConnected = false
                    serialPort.close()
                }
            }
        }
        //清除串口接收的数据
        btn_serial_clear.setOnClickListener {
            txt_serial_receive.text = ""
        }
        //发送串口数据
        btn_serial_send.setOnClickListener {
            serialSendMessage = edit_serial_send.text.toString()
            //添加换行符
            if (check_serial_add_newLine.isChecked) {
                serialSendMessage += "\r\n"
            }
            //添加回显
            if (check_serial_add_renew.isChecked) {
                txt_serial_receive.append(serialSendMessage)
            }
            //开始发送
            funSerialSend()
        }
    }

    //开启串口连接
    private fun funSerialConnect(): Boolean {
        // Find all available drivers from attached devices.
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        // Find all available drivers from attached devices.
        availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            txt_serial_info.text = "设备为空"
            return false
        }
        //得到第一个设备，一般对手机而言，也只有一个数据传输口可以接收串口数据
        //当然，添加了拓展坞的话就不一定了(好像现在为止还没有手机可以天机拓展坞)，
        //但那不在考虑范围之内
        txt_serial_info.text = availableDrivers[0].toString()
        // Open a connection to the first available driver.
        serialDriver = availableDrivers[0]
        val device = serialDriver.device
        val connection = manager.openDevice(device)
        //这里最好加上动态申请USB权限，防止第一次插入USB时已经拒绝
        if (connection == null) {
            txt_serial_info.text = "请重新拔插USB设备,并在弹出的窗口中选择本应用"
            return false
        }
        serialPort = serialDriver.ports[0] // Most devices have just one port (port 0)
        serialPort.open(connection)
        serialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        Log.e("", device.deviceName + " " + serialDriver.ports)

        var deviceType = String()
        var i = 0
        serialDriver.toString().split(".").forEach { s ->
            i++
            if (i == 6) {
                deviceType = s.split("@")[0]
            }
        }
        if (serialDriver.getPorts().size == 1)
            txt_serial_info.append(
                serialDriver.javaClass.simpleName.replace("SerialDriver", "")
            )
        else
            txt_serial_info.append(
                serialDriver.javaClass.simpleName
                    .replace("SerialDriver", "") + ", Port " + serialPort
            )
        txt_serial_info.append(
            String.format(
                Locale.US,
                "Vendor %04X, Product %04X",
                device.vendorId,
                device.productId
            )
        )

        //    "设备类型：" + deviceType + "\n设备名：" + device.deviceName + " \n设备ID：" + device.deviceId + " \n设备vendorId：" + device.vendorId + "\n设备版本：" + device.version + "\n" + device.productName
        return true
    }

    //字符串转unicode
    fun string2Unicode(string: String): String? {
        val unicode = StringBuffer()
        for (element in string) {
            // 取出每一个字符
            // 转换为unicode
            unicode.append(" " + Integer.toHexString(element.toInt()))
        }
        return unicode.toString()
    }
    //需要加一个子线程
    //毕竟是耗时操作
    private fun funSerialReceive() {
        while (serialConnected) {
            try {
                //串口读取次数大小和数组的长度无关
                val buffer = ByteArray(8192)
                var len: Int
                //无法在while中直接赋值
                do {
                    len = serialPort.read(buffer, READ_WAIT_MILLIS)
                    val string = String(buffer, 0, len, Charset.forName(encodingFormat))
                    if (string.isNotEmpty()) {
                        val message = Message()
                        val bundle = Bundle()
                        bundle.putString(HANDLER_RECEIVE_BUNDLE, string)
                        message.what = HANDLER_RECEIVE_MESSAGE_WHAT
                        message.data = bundle
                        handler.sendMessage(message)
                    }
                } while (len > 0)
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e(TAG, e.toString())
            }
        }
    }


    //发送不需要子线程
    private fun funSerialSend() {
        if (!serialConnected || !serialPort.isOpen) {
            Toast.makeText(this, "not connected", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "funSerialSend: 串口未打开")
        } else {
            try {
                val sendData = serialSendMessage.toByteArray(Charset.forName(encodingFormat))
                Log.e(TAG, "funSerialSend: $serialSendMessage")
                serialPort.write(sendData, WRITE_WAIT_MILLIS)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "funSerialSend: $e")
                Toast.makeText(this, "发送失败:$e", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                HANDLER_RECEIVE_MESSAGE_WHAT -> {
                    val string = msg.data.getString(HANDLER_RECEIVE_BUNDLE)
                    stringBuffer.append(string)
                    check_serial_receive_to16.setOnClickListener {
                        if (check_serial_receive_to16.isChecked) {
                            txt_serial_receive.text = string2Unicode(stringBuffer.toString())
                        } else {
                            txt_serial_receive.text = stringBuffer.toString()
                        }
                    }
                    if (check_serial_receive_to16.isChecked) {
                        txt_serial_receive.text = string2Unicode(stringBuffer.toString())?.toInt(16)
                            .toString()
                    } else {
                        txt_serial_receive.text = stringBuffer.toString()
                    }
                }
            }
        }
    }
}