package com.junior.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityHelperService : AccessibilityService() {

    companion object {
        var instance: AccessibilityHelperService? = null

        fun isEnabled(ctx: Context): Boolean {
            val enabled = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.contains(ctx.packageName, ignoreCase = true)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Navigate home (closes current app) */
    fun closeCurrentApp() = performGlobalAction(GLOBAL_ACTION_HOME)

    /** Press back */
    fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)

    /** Click a node that contains the given text */
    fun clickOnText(text: String) {
        val root = rootInActiveWindow ?: return
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            val target = if (node.isClickable) node else node.parent
            if (target?.isClickable == true) {
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }
    }

    /** Type text into the focused EditText */
    fun typeText(text: String) {
        val root = rootInActiveWindow ?: return
        val edits = findNodesByClass(root, "android.widget.EditText")
        for (node in edits) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            return
        }
    }

    fun scrollDown() = scrollAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    fun scrollUp()   = scrollAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)

    private fun scrollAction(action: Int) {
        val root = rootInActiveWindow ?: return
        scrollNode(root, action)
    }

    private fun scrollNode(node: AccessibilityNodeInfo, action: Int): Boolean {
        if (node.isScrollable) { node.performAction(action); return true }
        for (i in 0 until node.childCount) {
            if (node.getChild(i)?.let { scrollNode(it, action) } == true) return true
        }
        return false
    }

    private fun findNodesByClass(node: AccessibilityNodeInfo, className: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (node.className?.contains(className) == true) result.add(node)
        for (i in 0 until node.childCount) node.getChild(i)?.let { result.addAll(findNodesByClass(it, className)) }
        return result
    }
}
