package quest.saigon.town

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class NotificationHub {
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<(String) -> Unit>>()

    fun subscribe(userId: String, listener: (String) -> Unit): () -> Unit {
        listeners.getOrPut(userId) { CopyOnWriteArrayList() }.add(listener)
        return {
            listeners[userId]?.remove(listener)
            if (listeners[userId].isNullOrEmpty()) listeners.remove(userId)
        }
    }

    fun publish(userId: String, json: String) {
        listeners[userId]?.forEach { listener ->
            try {
                listener(json)
            } catch (_: Exception) {
            }
        }
    }
}
