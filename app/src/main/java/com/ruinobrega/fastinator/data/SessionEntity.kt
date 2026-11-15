package com.ruinobrega.fastinator.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ruinobrega.fastinator.model.Session

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long?,
) {
    fun toSession(): Session = Session(
        id = id,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
    )

    companion object {
        fun fromSession(session: Session): SessionEntity = SessionEntity(
            id = session.id,
            startTimeMillis = session.startTimeMillis,
            endTimeMillis = session.endTimeMillis,
        )
    }
}
