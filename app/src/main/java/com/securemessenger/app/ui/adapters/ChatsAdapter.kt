// مسیر: app/src/main/java/com/securemessenger/app/ui/adapters/ChatsAdapter.kt
package com.securemessenger.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.securemessenger.app.R
import com.securemessenger.app.data.models.ChatEntity
import com.securemessenger.app.databinding.ItemChatBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter برای نمایش لیست چت‌ها
 */
class ChatsAdapter(
    private val onChatClick: (ChatEntity) -> Unit
) : ListAdapter<ChatEntity, ChatsAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChatViewHolder(
        private val binding: ItemChatBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatEntity) {
            binding.apply {
                // نام کلاینت
                clientName.text = chat.clientName

                // آخرین پیام
                lastMessage.text = chat.lastMessage ?: itemView.context.getString(R.string.no_messages_yet)

                // زمان آخرین پیام
                messageTime.text = formatTime(chat.lastMessageTime)

                // تعداد پیام‌های خوانده نشده
                if (chat.unreadCount > 0) {
                    unreadBadge.visibility = View.VISIBLE
                    unreadBadge.text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString()
                } else {
                    unreadBadge.visibility = View.GONE
                }

                // آواتار (حرف اول نام)
                val firstLetter = chat.clientName.firstOrNull()?.uppercase() ?: "U"
                avatarText.text = firstLetter

                // رنگ آواتار بر اساس نام
                val colors = itemView.context.resources.getIntArray(R.array.avatar_colors)
                val colorIndex = chat.clientId.hashCode() % colors.size
                avatarText.setBackgroundColor(colors[Math.abs(colorIndex)])

                // وضعیت آنلاین (فعلاً همیشه مخفی)
                onlineIndicator.visibility = View.GONE

                // کلیک روی چت
                root.setOnClickListener {
                    onChatClick(chat)
                }
            }
        }

        /**
         * فرمت زمان برای نمایش
         */
        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60 * 1000 -> "الان" // کمتر از یک دقیقه
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} دقیقه پیش" // کمتر از یک ساعت
                diff < 24 * 60 * 60 * 1000 -> { // کمتر از یک روز
                    val formatter = SimpleDateFormat("HH:mm", Locale("fa", "IR"))
                    formatter.format(Date(timestamp))
                }
                diff < 7 * 24 * 60 * 60 * 1000 -> { // کمتر از یک هفته
                    val formatter = SimpleDateFormat("EEEE", Locale("fa", "IR"))
                    formatter.format(Date(timestamp))
                }
                else -> { // بیشتر از یک هفته
                    val formatter = SimpleDateFormat("yyyy/MM/dd", Locale("fa", "IR"))
                    formatter.format(Date(timestamp))
                }
            }
        }
    }
}

/**
 * DiffUtil برای بهینه‌سازی به‌روزرسانی لیست
 */
class ChatDiffCallback : DiffUtil.ItemCallback<ChatEntity>() {
    override fun areItemsTheSame(oldItem: ChatEntity, newItem: ChatEntity): Boolean {
        return oldItem.chatId == newItem.chatId
    }

    override fun areContentsTheSame(oldItem: ChatEntity, newItem: ChatEntity): Boolean {
        return oldItem == newItem
    }
}