package com.junior.assistant.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.junior.assistant.R

// ── Data Model ──────────────────────────────────────────
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// ── Adapter ─────────────────────────────────────────────
class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    companion object {
        private const val TYPE_USER   = 0
        private const val TYPE_JUNIOR = 1
    }

    fun addMessage(message: ChatMessage) {
        // Deduplication: skip identical consecutive Junior messages
        if (!message.isUser && messages.isNotEmpty()) {
            val last = messages.last()
            if (!last.isUser && last.text.trim() == message.text.trim()) return
        }
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun lastJuniorText(): String? = messages.lastOrNull { !it.isUser }?.text

    override fun getItemViewType(position: Int) =
        if (messages[position].isUser) TYPE_USER else TYPE_JUNIOR

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserVH(inflater.inflate(R.layout.item_chat_user, parent, false))
        } else {
            JuniorVH(inflater.inflate(R.layout.item_chat_junior, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        when (holder) {
            is UserVH   -> holder.bind(msg)
            is JuniorVH -> holder.bind(msg)
        }
    }

    override fun getItemCount() = messages.size

    // ── View Holders ──
    class UserVH(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view.findViewById(R.id.chatText)
        fun bind(msg: ChatMessage) { text.text = msg.text }
    }

    class JuniorVH(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view.findViewById(R.id.chatText)
        fun bind(msg: ChatMessage) { text.text = msg.text }
    }
}
