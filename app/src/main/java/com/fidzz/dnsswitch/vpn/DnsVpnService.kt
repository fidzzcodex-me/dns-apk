package com.fidzz.dnsswitch.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.fidzz.dnsswitch.MainActivity
import com.fidzz.dnsswitch.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

object VpnState {
    private val _connectedProviderId = MutableStateFlow<String?>(null)
    val connectedProviderId = _connectedProviderId.asStateFlow()

    fun setConnected(providerId: String?) {
        _connectedProviderId.value = providerId
    }
}

class DnsVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.fidzz.dnsswitch.CONNECT"
        const val ACTION_DISCONNECT = "com.fidzz.dnsswitch.DISCONNECT"
        const val EXTRA_PROVIDER_ID = "extra_provider_id"
        const val EXTRA_PRIMARY_DNS = "extra_primary_dns"
        const val EXTRA_SECONDARY_DNS = "extra_secondary_dns"
        const val EXTRA_PROVIDER_NAME = "extra_provider_name"

        private const val CHANNEL_ID = "dns_switch_vpn"
        private const val NOTIFICATION_ID = 42
        private const val VPN_ADDRESS = "10.111.222.1"
        private const val VPN_MTU = 1500
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var relayJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                val primary = intent.getStringExtra(EXTRA_PRIMARY_DNS) ?: return START_NOT_STICKY
                val secondary = intent.getStringExtra(EXTRA_SECONDARY_DNS) ?: primary
                val providerId = intent.getStringExtra(EXTRA_PROVIDER_ID)
                val providerName = intent.getStringExtra(EXTRA_PROVIDER_NAME) ?: "DNS Switch"
                startVpn(primary, secondary, providerId, providerName)
            }
        }
        return START_STICKY
    }

    private fun startVpn(primary: String, secondary: String, providerId: String?, providerName: String) {
        stopRelay()

        val builder = Builder()
            .setSession(providerName)
            .setMtu(VPN_MTU)
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(primary)
            .addDnsServer(secondary)
            .addRoute(primary, 32)
            .addRoute(secondary, 32)

        vpnInterface = builder.establish()

        if (vpnInterface == null) {
            VpnState.setConnected(null)
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification(providerName))
        VpnState.setConnected(providerId)

        relayJob = serviceScope.launch {
            runRelayLoop(vpnInterface!!, primary, secondary)
        }
    }

    private fun stopVpn() {
        stopRelay()
        vpnInterface?.close()
        vpnInterface = null
        VpnState.setConnected(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopRelay() {
        relayJob?.cancel()
        relayJob = null
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopRelay()
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }

    private suspend fun runRelayLoop(pfd: ParcelFileDescriptor, primaryDns: String, secondaryDns: String) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buffer = ByteArray(32767)

        while (true) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                break
            }
            if (length <= 0) continue

            val packet = buffer.copyOf(length)
            if (!isIPv4Udp(packet)) continue

            val ihl = (packet[0].toInt() and 0x0F) * 4
            if (packet.size < ihl + 8) continue

            val destPort = readUInt16(packet, ihl + 2)
            if (destPort != 53) continue

            val targetDns = if (readIp(packet, 16) == primaryDns) primaryDns else secondaryDns

            serviceScope.launch {
                relayDnsQuery(packet, ihl, targetDns, output)
            }
        }
    }

    private fun relayDnsQuery(requestPacket: ByteArray, ihl: Int, dnsIp: String, output: FileOutputStream) {
        val udpHeaderStart = ihl
        val payloadStart = udpHeaderStart + 8
        val payload = requestPacket.copyOfRange(payloadStart, requestPacket.size)

        val sourceIp = readIp(requestPacket, 12)
        val sourcePort = readUInt16(requestPacket, udpHeaderStart)

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            protect(socket)
            socket.soTimeout = 5000

            val request = DatagramPacket(payload, payload.size, InetSocketAddress(dnsIp, 53))
            socket.send(request)

            val responseBuffer = ByteArray(4096)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)

            val replyPacket = buildIpv4UdpPacket(
                sourceIp = dnsIp,
                destIp = sourceIp,
                sourcePort = 53,
                destPort = sourcePort,
                payload = responseBuffer.copyOf(responsePacket.length)
            )

            synchronized(output) {
                output.write(replyPacket)
            }
        } catch (e: Exception) {
        } finally {
            socket?.close()
        }
    }

    private fun isIPv4Udp(packet: ByteArray): Boolean {
        if (packet.isEmpty()) return false
        val version = (packet[0].toInt() and 0xF0) shr 4
        if (version != 4) return false
        val protocol = packet[9].toInt() and 0xFF
        return protocol == 17
    }

    private fun readUInt16(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun readIp(data: ByteArray, offset: Int): String {
        return "${data[offset].toInt() and 0xFF}.${data[offset + 1].toInt() and 0xFF}." +
            "${data[offset + 2].toInt() and 0xFF}.${data[offset + 3].toInt() and 0xFF}"
    }

    private fun ipToBytes(ip: String): ByteArray {
        return ip.split(".").map { it.toInt().toByte() }.toByteArray()
    }

    private fun buildIpv4UdpPacket(
        sourceIp: String,
        destIp: String,
        sourcePort: Int,
        destPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLength = 8 + payload.size
        val totalLength = 20 + udpLength
        val packet = ByteArray(totalLength)

        packet[0] = 0x45
        packet[1] = 0
        packet[2] = ((totalLength shr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        packet[4] = 0
        packet[5] = 0
        packet[6] = 0x40
        packet[7] = 0
        packet[8] = 64
        packet[9] = 17
        packet[10] = 0
        packet[11] = 0

        val srcBytes = ipToBytes(sourceIp)
        val dstBytes = ipToBytes(destIp)
        System.arraycopy(srcBytes, 0, packet, 12, 4)
        System.arraycopy(dstBytes, 0, packet, 16, 4)

        val ipChecksum = checksum(packet, 0, 20)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        packet[20] = ((sourcePort shr 8) and 0xFF).toByte()
        packet[21] = (sourcePort and 0xFF).toByte()
        packet[22] = ((destPort shr 8) and 0xFF).toByte()
        packet[23] = (destPort and 0xFF).toByte()
        packet[24] = ((udpLength shr 8) and 0xFF).toByte()
        packet[25] = (udpLength and 0xFF).toByte()
        packet[26] = 0
        packet[27] = 0

        System.arraycopy(payload, 0, packet, 28, payload.size)

        return packet
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i < end - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun buildNotification(providerName: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DNS Switch",
                NotificationManager.IMPORTANCE_LOW
            )
            manager?.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(providerName)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }
}
