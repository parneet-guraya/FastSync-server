package com.guraya.fastsync

import com.guraya.fastsync.DesktopShares.id
import com.guraya.fastsync.DesktopShares.name
import com.guraya.fastsync.DesktopShares.path
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.resolveColumnType
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.atomic.AtomicReference

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module).start(
        wait = true
    )
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
    initDatabase()
    createTables()

    routing {
        get("/") {
            call.respondText("Ktor:")
        }

        route("/mobile_shares") {
            get {
                dbQuery {
                    call.respond(HttpStatusCode.OK, MobileShares.selectAll().map {
                        Share(it[MobileShares.id], it[MobileShares.name], it[MobileShares.path])
                    }
                    )
                }
            }
            post {
                val shares = call.receive<List<Share>>()
//                println("Shares received $shares")
                shares.forEach { share ->
                    dbQuery {
                        MobileShares.insert {
                            it[name] = share.name
                            it[path] = share.path
                        }
                    }
                }
                call.respond(HttpStatusCode.Created, "Inserted")
            }

            delete {
                dbQuery {
                   try {
                       val encodedJsonParam = call.parameters["id"]
                       if (encodedJsonParam != null) {
                           val sharesToDelete = Json.decodeFromString<List<Int>>(encodedJsonParam)
                           sharesToDelete.onEach { shareId ->
                               MobileShares.deleteWhere { MobileShares.id.eq(shareId) }
                           }
                           call.respond("Delete operation Finish")
                       }
                   }catch (e: Exception){
                       println(e.message)
                       call.respond(status = HttpStatusCode.InternalServerError,"Error occurred while deleting")
                   }
                }
            }
        }



        route("/desktop_shares") {
            get {
                dbQuery {
                    val encodedJsonParam = call.parameters["getWithIds"]
                    if (encodedJsonParam != null) {
                        val listOfIds: List<Int> = Json.decodeFromString(encodedJsonParam)
                        val filteredList = mutableListOf<Share>()
                        DesktopShares.selectAll().where(id.inList(listOfIds)).map { row ->
                            val share = Share(row[id], row[name], row[path])
                            filteredList.add(share)
                        }
                        call.respond(filteredList)
                    } else {
                        call.respond(
                            DesktopShares.selectAll().map {
                                Share(
                                    it[DesktopShares.id],
                                    it[name],
                                    it[path]
                                )
                            }
                        )
                    }
                }
            }
            post {
                val shares = call.receive<List<Share>>()
                shares.forEach { share ->
                    dbQuery {
                        DesktopShares.insert {
                            it[name] = share.name
                            it[path] = share.path
                        }
                    }
                }
                call.respond(HttpStatusCode.Created, "Inserted")
            }

            delete {
                dbQuery {
                    try {
                        val encodedJsonParam = call.parameters["id"]
                        if (encodedJsonParam != null) {
                            val sharesToDelete = Json.decodeFromString<List<Int>>(encodedJsonParam)
                            sharesToDelete.onEach { shareId ->
                                DesktopShares.deleteWhere { DesktopShares.id.eq(shareId) }
                            }
                            call.respond("Delete operation Finish")
                        }
                    }catch (e: Exception){
                        println(e.message)
                        call.respond(status = HttpStatusCode.InternalServerError,"Error occurred while deleting")
                    }
                }
            }
        }
        val mobileConn: AtomicReference<DefaultWebSocketServerSession?> = AtomicReference()
        val desktopConn: AtomicReference<DefaultWebSocketServerSession?> = AtomicReference()
        webSocket(path = "/wsMobile") {
            println("Server connected mobile $this")
            mobileConn.set(this)

            val desktopSharesIdList = receiveDeserialized<Pair<List<Int>, String>?>()
            if (desktopSharesIdList != null) {
                if (desktopConn.get() != null) {
                    desktopConn.get()?.sendSerialized(desktopSharesIdList)
                } else {
                    println("Desktop session not available")
                }
            }
        }

        webSocket(path = "/wsDesktop") {
            println("Server connected desktop $this")

            desktopConn.set(this)
            receiveDeserialized()
        }
    }
}

fun initDatabase() {
    Database.connect("jdbc:sqlite:file_sharing.db", driver = "org.sqlite.JDBC")
}

object MobileShares : Table("mobile_shares") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val path = varchar("path", 1024)

    override val primaryKey = PrimaryKey(id)
}

object DesktopShares : Table("desktop_shares") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val path = varchar("path", 1024)

    override val primaryKey = PrimaryKey(id)
}

fun createTables() {
    transaction {
        SchemaUtils.create(MobileShares, DesktopShares)
    }
}

suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }

@Serializable
data class Share(val id: Int? = null, val name: String, val path: String)