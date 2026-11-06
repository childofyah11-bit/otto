package com.otto.paintbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoPaintAccessibilityService : AccessibilityService() {

    companion object {
        private const val GAME_PACKAGE = "com.pbn.pbox.oakever.coloring.drawing.paint.by.number.colour.art"
        private val PLAY_STORE_PACKAGES = setOf("com.android.vending", "com.google.android.finsky")
        private val CLOSE_TEXT = setOf("close", "x", "skip", "later", "not now", "no thanks")
        private val NEXT_TEXT = setOf("next", "continue", "play", "start", "new", "done")
        private val AD_TEXT = setOf("ad", "sponsored", "watch", "install", "reward")
        private val SAFE_TAP_WINDOW_MS = 500L
    }

    private var lastActionTimeMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SELECTED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            packageNames = arrayOf(GAME_PACKAGE, *PLAY_STORE_PACKAGES.toTypedArray())
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        if (PLAY_STORE_PACKAGES.contains(packageName) || packageName.contains("finsky")) {
            reopenGame()
            return
        }

        if (packageName != GAME_PACKAGE) return

        // Avoid spamming taps; one action per short cool-down window.
        val now = SystemClock.elapsedRealtime()
        if (now - lastActionTimeMs < SAFE_TAP_WINDOW_MS) return

        if (dismissAdIfPossible()) {
            lastActionTimeMs = now
            return
        }

        if (advanceIfPossible()) {
            lastActionTimeMs = now
            return
        }

        if (smartBoardTapIfPossible()) {
            lastActionTimeMs = now
        }
    }

    override fun onInterrupt() {
        Log.d("AutoPaintService", "Accessibility service interrupted")
    }

    private fun dismissAdIfPossible(): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidate = findFirstNode(root) { node -> isLikelyCloseButton(node) || isLikelyAdMarker(node) }
        if (candidate != null) {
            safePerformClick(candidate)
            return true
        }
        return false
    }

    private fun advanceIfPossible(): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidate = findFirstNode(root) { node -> isLikelyNextButton(node) }
        if (candidate != null) {
            safePerformClick(candidate)
            return true
        }
        return false
    }

    private fun smartBoardTapIfPossible(): Boolean {
        val root = rootInActiveWindow ?: return false

        // Try to find likely board cells and tap one. This is intentionally heuristic:
        // - only clickable nodes
        // - avoid giant controls like menu or full-screen overlays
        // - prefer the central area of the screen
        val candidate = findFirstNode(root) { node -> isLikelyPaintCell(node) }
        if (candidate != null) {
            safePerformClick(candidate)
            return true
        }

        // Last resort: if we see a color-swatches area or a generic paint action button,
        // try tapping the center-most clickable control in the play region.
        val fallback = findFirstNode(root) { node ->
            node.isClickable && node.className != null &&
                node.bounds != null &&
                isInGamePlayArea(node.bounds) &&
                node.bounds.width() in 24..220 &&
                node.bounds.height() in 24..220
        }
        if (fallback != null) {
            safePerformClick(fallback)
            return true
        }

        return false
    }

    private fun isLikelyCloseButton(node: AccessibilityNodeInfo): Boolean {
        if (!node.isClickable && !node.isEnabled) return false

        val text = (node.text?.toString() ?: "").lowercase()
        val desc = (node.contentDescription?.toString() ?: "").lowercase()
        val combined = "$text $desc"
        if (CLOSE_TEXT.any { combined.contains(it) }) return true

        // X is a common close symbol. Single-char X buttons are common in ads.
        if (combined == "x" || combined == "×") return true

        val bounds = node.bounds
        val screenWidth = rootInActiveWindow?.bounds?.width() ?: return false
        val screenHeight = rootInActiveWindow?.bounds?.height() ?: return false

        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val inCorner = centerX > screenWidth * 0.7f && centerY < screenHeight * 0.3f
        val tinyButton = bounds.width() < 120 && bounds.height() < 120
        val clickable = node.isClickable || node.isEnabled

        return clickable && inCorner && tinyButton
    }

    private fun isLikelyAdMarker(node: AccessibilityNodeInfo): Boolean {
        val text = (node.text?.toString() ?: "").lowercase()
        val desc = (node.contentDescription?.toString() ?: "").lowercase()
        val combined = "$text $desc"

        if (AD_TEXT.any { combined.contains(it) }) return true

        // Common ad UI patterns: "Sponsored", "Ad", or full-page overlays with large clickable area.
        val bounds = node.bounds
        val isLargeOverlay = bounds.width() > 400 && bounds.height() > 600
        val textish = combined.isNotBlank()
        return textish && isLargeOverlay && node.isClickable
    }

    private fun isLikelyNextButton(node: AccessibilityNodeInfo): Boolean {
        if (!node.isClickable && !node.isEnabled) return false

        val text = (node.text?.toString() ?: "").lowercase()
        val desc = (node.contentDescription?.toString() ?: "").lowercase()
        val combined = "$text $desc"
        if (NEXT_TEXT.any { combined.contains(it) }) return true

        val bounds = node.bounds
        val screenWidth = rootInActiveWindow?.bounds?.width() ?: return false
        val centerX = bounds.centerX()
        val width = bounds.width()
        val isBottomAction = centerX in (screenWidth * 0.2f..screenWidth * 0.8f)
        val reasonableSize = width in 80..400 && bounds.height() in 40..220
        return isBottomAction && reasonableSize && node.isClickable
    }

    private fun isLikelyPaintCell(node: AccessibilityNodeInfo): Boolean {
        if (!node.isClickable && !node.isEnabled) return false

        val text = (node.text?.toString() ?: "").lowercase()
        val desc = (node.contentDescription?.toString() ?: "").lowercase()
        val combined = "$text $desc"
        if (combined.contains("color") || combined.contains("paint") || combined.contains("fill")) {
            return true
        }

        val bounds = node.bounds
        if (!isInGamePlayArea(bounds)) return false

        val sizeSmallEnough = bounds.width() in 18..180 && bounds.height() in 18..180
        val notHuge = bounds.width() < (rootInActiveWindow?.bounds?.width() ?: Int.MAX_VALUE) * 0.6f
        val notOverlayLike = bounds.height() < (rootInActiveWindow?.bounds?.height() ?: Int.MAX_VALUE) * 0.6f

        return sizeSmallEnough && notHuge && notOverlayLike && node.isClickable
    }

    private fun isInGamePlayArea(bounds: Rect): Boolean {
        val rootBounds = rootInActiveWindow?.bounds ?: return false
        val margin = 0.12f
        val leftMin = (rootBounds.left + rootBounds.width() * margin).toInt()
        val rightMax = (rootBounds.right - rootBounds.width() * margin).toInt()
        val topMin = (rootBounds.top + rootBounds.height() * margin).toInt()
        val bottomMax = (rootBounds.bottom - rootBounds.height() * margin).toInt()
        return bounds.centerX() in leftMin..rightMax && bounds.centerY() in topMin..bottomMax
    }

    private fun findFirstNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            if (predicate(node)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return null
    }

    private fun safePerformClick(node: AccessibilityNodeInfo): Boolean {
        return try {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                true
            } else {
                // Some nodes can still be clicked via parent wrappers.
                val parent = node.parent ?: return false
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun reopenGame() {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(GAME_PACKAGE)
            ?: return
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(launchIntent)
        } catch (_: Exception) {
            Log.e("AutoPaintService", "Failed to reopen the game after Play Store opened.")
        }
    }
}

