package com.powerz.km003c

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.powerz.km003c.ui.PowerZApp
import com.powerz.km003c.ui.theme.PowerZTheme
import com.powerz.km003c.usb.Km003cUsbManager
import com.powerz.km003c.viewmodel.PowerZViewModel

class MainActivity : ComponentActivity() {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.powerz.km003c.USB_PERMISSION"
    }

    private val viewModel: PowerZViewModel by viewModels()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (device != null) {
                        viewModel.onUsbPermissionResult(device, granted)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    if (device != null && Km003cUsbManager.isSupportedDevice(device)) {
                        viewModel.scanAndConnect()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (device != null) {
                        viewModel.onUsbDetached(device)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.onRequestUsbPermission = { device ->
            val permIntent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val pi = PendingIntent.getBroadcast(this, 0, permIntent, flags)
            viewModel.usbManager.getUsbManager().requestPermission(device, pi)
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            this,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            PowerZTheme {
                PowerZApp(
                    viewModel = viewModel,
                    onExportCsv = {
                        val shareIntent = viewModel.createCsvShareIntent()
                        if (shareIntent != null) {
                            startActivity(Intent.createChooser(shareIntent, "CSV 측정 데이터 내보내기"))
                        } else {
                            Toast.makeText(this, "내보낼 측정 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // Auto-connect if KM003C is already attached
        viewModel.scanAndConnect()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            viewModel.scanAndConnect()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(usbReceiver) }
    }
}
