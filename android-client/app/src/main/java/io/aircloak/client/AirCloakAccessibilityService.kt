package io.aircloak.client

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * AirCloakAccessibilityService
 * Scoped Accessibility Service that intercepts text inputs, clipboard paste triggers,
 * scans for plaintext secrets on-device, and replaces them with safe mock tokens.
 */
class AirCloakAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AirCloakAccessibility"
        
        // Regex signatures matching known secret formats
        private val AWS_PATTERN = Regex("\\b(AKIA[0-9A-Z]{16})\\b")
        private val STRIPE_PATTERN = Regex("\\b(sk_live_[0-9a-zA-Z]{24,34})\\b")
        private val IP_PATTERN = Regex("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b")
        private val GENERIC_KEY_PATTERN = Regex("(?i)\\b(?:bearer|token|secret|password)\\s*[:=]\\s*['\"]?([a-zA-Z0-9_\\-]{24,})['\"]?")
    }

    private lateinit var clipboardManager: ClipboardManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        Log.i(TAG, "AirCloak Accessibility Service Connected & Armed.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val sourceNode = event.source ?: return
                inspectAndSanitizeNode(sourceNode)
            }
        }
    }

    /**
     * Traverses the node, scans text for leakable secrets, and executes on-device sanitization.
     */
    private fun inspectAndSanitizeNode(node: AccessibilityNodeInfo) {
        val text = node.text?.toString() ?: return

        var sanitized = text
        var detected = false

        if (AWS_PATTERN.containsMatchIn(sanitized)) {
            sanitized = AWS_PATTERN.replace(sanitized, "AKIA_MOCK_AIRCLOAK_SECURE")
            detected = true
        }
        if (STRIPE_PATTERN.containsMatchIn(sanitized)) {
            sanitized = STRIPE_PATTERN.replace(sanitized, "sk_live_mock_aircloak_token")
            detected = true
        }
        if (GENERIC_KEY_PATTERN.containsMatchIn(sanitized)) {
            sanitized = GENERIC_KEY_PATTERN.replace(sanitized, "bearer: [AIRCLOAK_SECURE_TOKEN]")
            detected = true
        }

        if (detected && node.isEditable) {
            Log.w(TAG, "Sensitive credential detected in UI node! Performing real-time text sanitization.")
            
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, sanitized)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            Toast.makeText(applicationContext, "🛡️ AirCloak: Intercepted & Sanitized Secret in Input Field", Toast.LENGTH_SHORT).show()
        }

        // Recursively inspect child nodes
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                inspectAndSanitizeNode(child)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AirCloak Accessibility Service Interrupted.")
    }
}
