package com.hoshi.back

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.html.respondHtml
import io.ktor.http.ContentType
import io.ktor.http.cio.websocket.DefaultWebSocketSession
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.css.*
import kotlinx.html.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.LinkedHashSet

//  http://127.0.0.1:8080
fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalLocationsAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

  install(WebSockets)
  install(Locations)

  val client = HttpClient(Apache) {
  }

  routing {
    get("/") {
      call.respondText("HELLO WORLD!", contentType = ContentType.Text.Plain)
    }

    get("/html-dsl") {
      call.respondHtml {
        body {
          h1 { +"HTML" }
          ul {
            for (n in 1..10) {
              li { +"$n" }
            }
          }
        }
      }
    }

    get("/styles.css") {
      call.respondCss {
        body {
          backgroundColor = Color.red
        }
        p {
          fontSize = 2.em
        }
        rule("p.myclass") {
          color = Color.blue
        }
      }
    }

    // 简单示例，连接上后，对用户发送的消息进行复读
    /*webSocket("/chat") {
      // this: DefaultWebSocketSession
      while (true) {
        val frame = incoming.receive() // suspend
        when (frame) {
          is Frame.Text -> {
            val text = frame.readText()
            outgoing.send(Frame.Text(text)) // suspend
          }
        }
      }
    }*/

    // 用来保存一组打开的链接，由于 Ktor 默认是多线程的，因此我们应该使用线程安全的集合或者以 newSingleThreadContext 将代码体限制为单线程
    // val wsConnections = Collections.synchronizedSet(LinkedHashSet<DefaultWebSocketSession>())

    val clients = Collections.synchronizedSet(LinkedHashSet<ChatClient>())
    webSocket("/chat") {
      // this: DefaultWebSocketSession
      val client = ChatClient(this)
      clients += client
      try {
        while (true) {
          val frame = incoming.receive()
          when (frame) {
            is Frame.Text -> {
              val text = frame.readText()
              // 迭代所有连接
              val textToSend = "${client.name} said: $text"
              for (other in clients.toList()) {
                other.session.outgoing.send(Frame.Text(textToSend))
              }
            }
          }
        }
      } finally {
        clients -= client
      }
    }
  }
}

/**
 * 聊天室客户端实体类
 */
class ChatClient(val session: DefaultWebSocketSession) {
  companion object { var lastId = AtomicInteger(0) }
  val id = lastId.getAndIncrement()
  val name = "user$id"
}

fun FlowOrMetaDataContent.styleCss(builder: CSSBuilder.() -> Unit) {
  style(type = ContentType.Text.CSS.toString()) {
    +CSSBuilder().apply(builder).toString()
  }
}

fun CommonAttributeGroupFacade.style(builder: CSSBuilder.() -> Unit) {
  this.style = CSSBuilder().apply(builder).toString().trim()
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
  this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}
