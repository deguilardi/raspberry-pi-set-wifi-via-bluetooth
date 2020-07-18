package app.carebnb.raspberrysetup

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.ArraySet
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*
import kotlin.concurrent.thread

class MainActivity: AppCompatActivity() {

    companion object {
        const val PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 12345
        val uuid: UUID = UUID.fromString("815425a5-bfac-47bf-9321-c5ff980b5e11")
    }

    inner class WifiScanReceiver : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val wifiScanList: List<ScanResult> = wifiManager.scanResults
            val set: MutableSet<String> = mutableSetOf()
            wifiScanList.forEach { item ->
                if(!item.SSID.isNullOrEmpty()) {
                    set.add(item.SSID)
                }
            }
            val spinnerArrayAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, set.toList())
            spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            wifi_spinner.adapter = spinnerArrayAdapter
            writeOutput("Scanning completed.")
        }
    }

    private lateinit var wifiManager: WifiManager
    private lateinit var wifiReciever: WifiScanReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        psk_text.setOnClickListener { refreshDevices() }
        refreshWifi_button.setOnClickListener{ refreshWifi() }
        start_button.setOnClickListener{
            val ssid: String = wifi_spinner.selectedItem.toString()
            val psk: String = psk_text.text.toString()
            val device = devices_spinner.selectedItem as BluetoothDevice
            thread {
                workerThread(ssid, psk, device)
            }
        }

        wifiReciever = WifiScanReceiver()
        registerReceiver(wifiReciever, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        refreshWifi()
        refreshDevices()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            wifiScan()
        }
    }

    private fun refreshDevices() {
        val adapterDevices = DeviceAdapter(this, R.layout.spinner_devices, ArrayList())
        devices_spinner.adapter = adapterDevices
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled) {
            val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetooth, 0)
        }
        val pairedDevices = mBluetoothAdapter!!.bondedDevices
        if (pairedDevices.size > 0) {
            for (device in pairedDevices) {
                adapterDevices.add(device)
            }
        }
    }

    private fun refreshWifi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION)
        } else {
            wifiScan()
        }
    }

    private fun wifiScan(){
        writeOutput("Scanning for wifi networks...")
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.startScan()
    }

    private fun writeOutput(text: String) {
        runOnUiThread {
            messages_text.text = ("${messages_text.text}\n${text}")
        }
    }

    private fun clearOutput() {
        runOnUiThread {
            messages_text.text = ""
        }
    }

    private fun workerThread(ssid: String, psk: String, device: BluetoothDevice) {
        clearOutput()
        writeOutput("Starting config update.")
        writeOutput("Network: $ssid")
        writeOutput("Device: " + device.name + " - " + device.address)
        try {
            val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            if (!socket.isConnected) {
                socket.connect()
                Thread.sleep(1000)
            }
            writeOutput("Connected.")
            val outputStream: OutputStream = socket.outputStream
            writeOutput("Output stream OK.")
            val inputStream: InputStream = socket.inputStream
            writeOutput("Input stream OK.")
            waitForResponse(inputStream, -1)
            writeOutput("Sending SSID.")
            outputStream.write(ssid.toByteArray())
            outputStream.flush()
            waitForResponse(inputStream, -1)
            writeOutput("Sending PSK.")
            outputStream.write(psk.toByteArray())
            outputStream.flush()
            waitForResponse(inputStream, -1)
            socket.close()
            writeOutput("Success.")
        } catch (e: Exception) {
            e.printStackTrace()
            writeOutput("Failed.")
        }
        writeOutput("Done.")
    }

    /*
     * TODO actually use the timeout
     */
    @Throws(IOException::class)
    private fun waitForResponse(mmInputStream: InputStream, timeout: Long) {
        val delimiter: Byte = 33
        var readBufferPosition = 0
        var bytesAvailable: Int
        while (true) {
            bytesAvailable = mmInputStream.available()
            if (bytesAvailable > 0) {
                val packetBytes = ByteArray(bytesAvailable)
                val readBuffer = ByteArray(1024)
                mmInputStream.read(packetBytes)
                for (i in 0 until bytesAvailable) {
                    val b = packetBytes[i]
                    if (b == delimiter) {
                        val encodedBytes = ByteArray(readBufferPosition)
                        System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.size)
                        val data = String(encodedBytes, Charset.forName("US-ASCII"))
                        writeOutput("Received:$data")
                        return
                    } else {
                        readBuffer[readBufferPosition++] = b
                    }
                }
            }
        }
    }
}