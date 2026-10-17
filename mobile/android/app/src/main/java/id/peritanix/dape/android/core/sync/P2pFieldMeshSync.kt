package id.peritanix.dape.android.core.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * Tahap 7.1: Offline Peer-to-Peer (P2P) Field Mesh Sync & Data Mule Orchestrator.
 *
 * Enables data synchronization between worker smartphones in remote mountain fields
 * without internet access using local P2P mesh protocols (BLE / Wi-Fi Direct).
 * Uses CRDT (Conflict-Free Replicated Data Types) with vector timestamps.
 *
 * Implements the "Data Mule" pattern: a single worker phone that returns home to internet
 * uploads the merged cluster records to the cloud server and brings back updated model weights.
 */
class P2pFieldMeshSync(val workerDeviceId: String = "WORKER-ALPHA") {

    data class MeshSyncPacket(
        val packetId: String,
        val originWorkerId: String,
        val targetRole: String, // "MANDOR" or "DATA_MULE"
        val timestampMs: Long,
        val mutationCount: Int,
        val vectorClock: Long,
        val payloadJson: String,
        val payloadHashSha256: String
    )

    data class MergeResult(
        val mergedCount: Int,
        val conflictsResolved: Int,
        val latestVectorClock: Long,
        val isMuleReadyForCloudUpload: Boolean,
        val statusMessage: String
    )

    private var localVectorClock: Long = 1000L

    /**
     * Packs offline farm mutations (diagnostics, soil tests, harvest batches) into a P2P packet.
     */
    fun packLocalMutations(mutations: List<JSONObject>): MeshSyncPacket {
        localVectorClock++
        val array = JSONArray()
        for (m in mutations) {
            m.put("synced_via_mesh", true)
            m.put("origin_worker", workerDeviceId)
            m.put("mesh_vclock", localVectorClock)
            array.put(m)
        }

        val jsonStr = array.toString()
        val hash = sha256(jsonStr)

        return MeshSyncPacket(
            packetId = "MESH-${System.currentTimeMillis()}-$workerDeviceId",
            originWorkerId = workerDeviceId,
            targetRole = "DATA_MULE",
            timestampMs = System.currentTimeMillis(),
            mutationCount = mutations.size,
            vectorClock = localVectorClock,
            payloadJson = jsonStr,
            payloadHashSha256 = hash
        )
    }

    /**
     * Merges incoming peer packet using Last-Write-Wins (LWW) CRDT resolution.
     */
    fun mergePeerPacket(packet: MeshSyncPacket, localQueue: MutableList<JSONObject>): MergeResult {
        val incomingArray = JSONArray(packet.payloadJson)
        var resolved = 0
        var merged = 0

        for (i in 0 until incomingArray.length()) {
            val item = incomingArray.getJSONObject(i)
            val incomingId = item.optString("id", "")
            val existing = localQueue.find { it.optString("id") == incomingId }

            if (existing != null) {
                // Conflict resolution via vector clock
                val existingClock = existing.optLong("mesh_vclock", 0L)
                val incomingClock = item.optLong("mesh_vclock", 0L)
                if (incomingClock > existingClock) {
                    localQueue.remove(existing)
                    localQueue.add(item)
                    resolved++
                }
            } else {
                localQueue.add(item)
                merged++
            }
        }

        if (packet.vectorClock > localVectorClock) {
            localVectorClock = packet.vectorClock + 1
        }

        val totalRecords = localQueue.size
        val ready = totalRecords > 0

        val msg = "Sinkronisasi P2P Berhasil: $merged mutasi baru ditambahkan, $resolved konflik diselesaikan via CRDT. " +
                "Total $totalRecords catatan terkumpul di Data Mule untuk diunggah ke cloud saat tiba di rumah."

        return MergeResult(
            mergedCount = merged,
            conflictsResolved = resolved,
            latestVectorClock = localVectorClock,
            isMuleReadyForCloudUpload = ready,
            statusMessage = msg
        )
    }

    private fun sha256(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
