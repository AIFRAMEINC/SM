// مسیر: app/src/main/java/com/securemessenger/app/data/database/DatabaseDAO.kt
package com.securemessenger.app.data.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.securemessenger.app.data.models.ChatEntity
import com.securemessenger.app.data.models.ClientEntity
import com.securemessenger.app.data.models.MessageEntity

/**
 * DAO برای مدیریت پیام‌ها
 */
@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesByChatId(chatId: String): LiveData<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesByChatIdSync(chatId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllMessagesFromChat(chatId: String)

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): MessageEntity?

    @Query("UPDATE messages SET isRead = 1 WHERE chatId = :chatId AND senderId != :currentUserId")
    suspend fun markMessagesAsRead(chatId: String, currentUserId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND senderId != :currentUserId AND isRead = 0")
    suspend fun getUnreadMessageCount(chatId: String, currentUserId: String): Int
}

/**
 * DAO برای مدیریت چت‌ها
 */
@Dao
interface ChatDao {

    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    fun getAllChats(): LiveData<List<ChatEntity>>

    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    suspend fun getAllChatsSync(): List<ChatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Delete
    suspend fun deleteChat(chat: ChatEntity)

    @Query("SELECT * FROM chats WHERE chatId = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Query("UPDATE chats SET unreadCount = :count WHERE chatId = :chatId")
    suspend fun updateUnreadCount(chatId: String, count: Int)

    @Query("UPDATE chats SET lastMessage = :message, lastMessageTime = :time WHERE chatId = :chatId")
    suspend fun updateLastMessage(chatId: String, message: String, time: Long)
}

/**
 * DAO برای مدیریت اطلاعات کلاینت‌ها
 */
@Dao
interface ClientDao {

    @Query("SELECT * FROM clients ORDER BY lastSeen DESC")
    fun getAllClients(): LiveData<List<ClientEntity>>

    @Query("SELECT * FROM clients ORDER BY lastSeen DESC")
    suspend fun getAllClientsSync(): List<ClientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: ClientEntity)

    @Update
    suspend fun updateClient(client: ClientEntity)

    @Delete
    suspend fun deleteClient(client: ClientEntity)

    @Query("SELECT * FROM clients WHERE clientId = :clientId")
    suspend fun getClientById(clientId: String): ClientEntity?

    @Query("UPDATE clients SET lastSeen = :lastSeen WHERE clientId = :clientId")
    suspend fun updateLastSeen(clientId: String, lastSeen: Long)

    @Query("DELETE FROM clients WHERE lastSeen < :cutoffTime")
    suspend fun deleteOldClients(cutoffTime: Long)
}