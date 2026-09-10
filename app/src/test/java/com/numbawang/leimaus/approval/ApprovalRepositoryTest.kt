package com.numbawang.leimaus.approval

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ApprovalRepositoryTest {
    private class Server {
        val requests = mutableListOf<Pair<String, Boolean>>()
        var read = periodJson()
        var mutationResponse = envelope("hyvaksyJakso", mapOf("jakso" to periodJson(approved = true), "errors" to null))
        var mutationFailure: Exception? = null
        val repository = ApprovalRepository({ payload, mutation ->
            requests += payload to mutation
            if (mutation) {
                mutationFailure?.let { throw it }
                mutationResponse
            } else if (payload.contains("kellokorttiTyovuorot")) {
                envelope("tyovuorot", mapOf("jaksot" to listOf(read,
                    periodJson(id = 102, month = "2026-09"), periodJson(id = 103, type = 1)), "errors" to null))
            } else envelope("jaksoById", read)
        }, { "2026-08" })
    }

    @Test fun `opening reads completed actual period and preserves summary scalars without writes`() = runTest {
        val server = Server()
        val periods = server.repository.load("2026-08")
        assertEquals(listOf(101), periods.map { it.id })
        val fresh = server.repository.refresh(periods.single().id)
        assertEquals(samplePeriod(), fresh)
        assertTrue(server.requests.none { it.second })
        val request = (approvalJson.fromJson(server.requests.first().first) as List<*>).single() as Map<*, *>
        assertTrue(request["query"].toString().contains("tyovuorot"))
        val variables = request["variables"] as Map<*, *>
        assertEquals(ApprovalCalendar.monthRange("2026-08").first, (variables["from"] as Number).toLong())
    }

    @Test fun `approval refreshes then sends only true and requires confirmed server state`() = runTest {
        val server = Server()
        val result = server.repository.approve(samplePeriod(), "2026-08")
        assertTrue(result.approved)
        assertEquals(listOf(false, true), server.requests.map { it.second })
        val request = (approvalJson.fromJson(server.requests.last().first) as List<*>).single() as Map<*, *>
        val variables = request["variables"] as Map<*, *>
        assertEquals(true, variables["value"])
        assertEquals(101, (variables["jaksoid"] as Number).toInt())
    }

    @Test fun `already approved period is idempotent without mutation`() = runTest {
        val server = Server().apply { read = periodJson(approved = true) }
        assertTrue(server.repository.approve(samplePeriod(), "2026-08").approved)
        assertTrue(server.requests.none { it.second })
    }

    @Test fun `current month rejected before any request`() = runTest {
        val server = Server()
        assertFails { server.repository.load("2026-09") }
        assertFails { server.repository.approve(samplePeriod(), "2026-09") }
        assertTrue(server.requests.isEmpty())
    }

    @Test fun `changed totals locked and incomplete periods cannot be approved`() = runTest {
        for (response in listOf(periodJson(total = "160:00"), periodJson(locked = true), periodJson(total = null),
                periodJson() + ("henkiloid" to 999), periodJson() - "henkiloid", periodJson() - "jaksotila")) {
            val server = Server().apply { read = response }
            assertFails { server.repository.approve(samplePeriod(), "2026-08") }
            assertTrue(server.requests.none { it.second })
        }
    }

    @Test fun `wrong period or current dates returned by refresh never reach mutation`() = runTest {
        for (response in listOf(periodJson(id = 999), periodJson(month = "2026-09"))) {
            val server = Server().apply { read = response }
            assertFails { server.repository.approve(samplePeriod(), "2026-08") }
            assertTrue(server.requests.none { it.second })
        }
    }

    @Test fun `partial GraphQL success missing confirmation and malformed responses are failures`() = runTest {
        val responses = listOf(
            envelope("hyvaksyJakso", mapOf("jakso" to periodJson(), "errors" to null)),
            envelope("hyvaksyJakso", mapOf("jakso" to periodJson(approved = true), "errors" to listOf(mapOf("message" to "Ei oikeutta")))),
            envelope("hyvaksyJakso", mapOf("jakso" to periodJson(id = 999, approved = true))),
            "[{\"errors\":[{\"message\":\"Istunto vanhentunut\"}]}]",
            "[{\"data\":{}}]", "not json", "[]")
        for (response in responses) {
            val server = Server().apply { mutationResponse = response }
            assertFails { server.repository.approve(samplePeriod(), "2026-08") }
            assertEquals(1, server.requests.count { it.second })
        }
    }

    @Test fun `ambiguous network failure is never replayed`() = runTest {
        val server = Server().apply { mutationFailure = IOException("timeout after sending") }
        assertFails { server.repository.approve(samplePeriod(), "2026-08") }
        assertEquals(1, server.requests.count { it.second })
        server.read = periodJson(approved = true)
        assertTrue(server.repository.approve(samplePeriod(), "2026-08").approved)
        assertEquals(1, server.requests.count { it.second })
    }

    private suspend fun assertFails(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: Exception) { }
    }
}
