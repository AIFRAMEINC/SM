// مسیر: app/src/main/java/com/securemessenger/app/ui/adapters/MessagesAdapter.kt
package com.securemessenger.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.securemessenger.app.R
import com.securemessenger.app.data.models.MessageEntity
import com.securemessenger.app.data.models.MessageType
import com.securemessenger.app.databinding.ItemMessageReceivedBinding
import com.securemessenger.app.databinding.ItemMessageSentBinding
import io.noties.markwon.Markwon
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter برای نمایش پیام‌ها در چت
 */
class MessagesAdapter(
    private val currentUserId: String
) : ListAdapter<MessageEntity, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    private var markwon: Markwon? = null

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return when (message.messageType) {
            MessageType.SENT -> VIEW_TYPE_SENT
            MessageType.RECEIVED -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // Initialize Markwon for Markdown rendering
        if (markwon == null) {
            markwon = Markwon.create(parent.context)
        }

        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val binding = ItemMessageSentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                SentMessageViewHolder(binding)
            }
            VIEW_TYPE_RECEIVED -> {
                val binding = ItemMessageReceivedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ReceivedMessageViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
        }
    }

    /**
     * ViewHolder برای پیام‌های ارسال شده
     */
    inner class SentMessageViewHolder(
        private val binding: ItemMessageSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: MessageEntity) {
            binding.apply {
                // متن پیام با پشتیبانی از Markdown
                markwon?.setMarkdown(messageText, message.message) ?: run {
                    messageText.text = message.message
                }

                // زمان پیام
                messageTime.text = formatTime(message.timestamp)

                // وضعیت تحویل
                deliveryStatus.setImageResource(
                    if (message.isDelivered) {
                        if (message.isRead) R.drawable.ic_check_double
                        else R.drawable.ic_check
                    } else {
                        R.drawable.ic_clock
                    }
                )

                // تنظیم رنگ بر اساس وضعیت
                val tintColor = when {
                    message.isRead -> itemView.context.getColor(R.color.colorSuccess)
                    message.isDelivered -> itemView.context.getColor(R.color.colorSecondaryLight)
                    else -> itemView.context.getColor(R.color.colorSecondary)
                }
                deliveryStatus.setColorFilter(tintColor)

                // نمایش اطلاعات رمزنگاری (اختیاری)
                encryptionInfo.text = itemView.context.getString(R.string.encrypted_message)
            }
        }
    }

    /**
     * ViewHolder برای پیام‌های دریافت شده
     */
    inner class ReceivedMessageViewHolder(
        private val binding: ItemMessageReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: MessageEntity) {
            binding.apply {
                // متن پیام با پشتیبانی از Markdown
                markwon?.setMarkdown(messageText, message.message) ?: run {
                    messageText.text = message.message
                }

                // زمان پیام
                messageTime.text = formatTime(message.timestamp)

                // آواتار فرستنده
                val firstLetter = message.senderId.take(1).uppercase()
                avatarText.text = firstLetter

                // رنگ آواتار بر اساس senderId
                val colors = itemView.context.resources.getIntArray(R.array.avatar_colors)
                val colorIndex = message.senderId.hashCode() % colors.size
                avatarText.setBackgroundColor(colors[Math.abs(colorIndex)])

                // نام فرستنده (مخفی در چت دو نفره)
                senderName.text = message.senderId
                senderName.visibility = android.view.View.GONE

                // نمایش اطلاعات رمزنگاری (اختیاری)
                encryptionInfo.text = itemView.context.getString(R.string.encrypted_message)
            }
        }
    }

    /**
     * فرمت زمان برای نمایش در پیام‌ها
     */
    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60 * 1000 -> "الان" // کمتر از یک دقیقه
            diff < 24 * 60 * 60 * 1000 -> { // کمتر از یک روز
                val formatter = SimpleDateFormat("HH:mm", Locale("fa", "IR"))
                formatter.format(Date(timestamp))
            }
            diff < 7 * 24 * 60 * 60 * 1000 -> { // کمتر از یک هفته
                val formatter = SimpleDateFormat("EEEE HH:mm", Locale("fa", "IR"))
                formatter.format(Date(timestamp))
            }
            else -> { // بیشتر از یک هفته
                val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("fa", "IR"))
                formatter.format(Date(timestamp))
            }
        }
    }
}

/**
 * DiffUtil برای بهینه‌سازی به‌روزرسانی لیست پیام‌ها
 */
class MessageDiffCallback : DiffUtil.ItemCallback<MessageEntity>() {
    override fun areItemsTheSame(oldItem: MessageEntity, newItem: MessageEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: MessageEntity, newItem: MessageEntity): Boolean {
        return oldItem == newItem
    }
}