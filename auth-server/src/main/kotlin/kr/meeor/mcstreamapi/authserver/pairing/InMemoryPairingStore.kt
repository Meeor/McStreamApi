package kr.meeor.mcstreamapi.authserver.pairing

class InMemoryPairingStore {
    private val sessions = LinkedHashMap<String, PairingSession>()

    fun create(session: PairingSession): PairingSession =
        synchronized(sessions) {
            require(!sessions.containsKey(session.pairingCode)) {
                "Pairing session already exists. pairingCode=${session.pairingCode}"
            }
            sessions[session.pairingCode] = session
            session
        }

    fun find(pairingCode: String): PairingSession? =
        synchronized(sessions) {
            sessions[pairingCode]
        }

    fun update(
        pairingCode: String,
        transform: (PairingSession) -> PairingSession,
    ): PairingSession =
        synchronized(sessions) {
            val current = sessions[pairingCode]
                ?: throw PairingException.NotFound(pairingCode)
            val updated = transform(current)
            sessions[pairingCode] = updated
            updated
        }

    fun <T> updateWithResult(
        pairingCode: String,
        transform: (PairingSession) -> Pair<T, PairingSession>,
    ): T =
        synchronized(sessions) {
            val current = sessions[pairingCode]
                ?: throw PairingException.NotFound(pairingCode)
            val (result, updated) = transform(current)
            sessions[pairingCode] = updated
            result
        }

    fun remove(pairingCode: String): PairingSession? =
        synchronized(sessions) {
            sessions.remove(pairingCode)
        }

    fun removeIf(predicate: (PairingSession) -> Boolean): Int =
        synchronized(sessions) {
            val keys = sessions.values
                .filter(predicate)
                .map { it.pairingCode }
            keys.forEach(sessions::remove)
            keys.size
        }

    fun snapshot(): List<PairingSession> =
        synchronized(sessions) {
            sessions.values.toList()
        }
}
