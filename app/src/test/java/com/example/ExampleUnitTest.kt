package com.example

import org.junit.Assert.*
import org.junit.Test
import org.jsoup.Jsoup

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun fetchHtml() {
    try {
      println("--- FETCHING GIA LAI TV SCHEDULE PAGE ---")
      val doc = Jsoup.connect("https://gialaitv.vn/lich-phat-song/?ngay=2026-05-28&kenh=1")
          .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
          .timeout(10000)
          .get()
      
      println("TITLE: " + doc.title())
      
      // Let's print out some elements with relevant tags/classes
      println("--- TABLES ---")
      val tables = doc.select("table")
      println("Found tables count: ${tables.size}")
      for ((idx, table) in tables.withIndex()) {
          println("Table $idx classes: ${table.className()} id: ${table.id()}")
          // Let's inspect some row content
          val rows = table.select("tr")
          println("  Row count: ${rows.size}")
          if (rows.isNotEmpty()) {
              println("  First row content: ${rows.first()?.text()}")
          }
      }
      
      println("--- GENERAL SCHEDULE BLOCKS ---")
      val divs = doc.select("div")
      val potentialDivs = divs.filter { it.className().contains("lich") || it.className().contains("schedule") || it.className().contains("program") }
      println("Found potential divs count: ${potentialDivs.size}")
      for (div in potentialDivs.take(5)) {
          println("  Class: ${div.className()} -> text: ${div.text().take(100)}")
      }

      println("--- LIST ITEMS OR PARAGRAPHS CONTAINING COLONS (TIMES) ---")
      val allElements = doc.getAllElements()
      val timeElements = allElements.filter { it.tagName() in listOf("strong", "span", "td", "div") && it.text().matches(Regex("^\\s*\\d{1,2}\\s*:\\s*\\d{2}.*$")) }
      println("Found potential time elements matching HH:MM count: ${timeElements.size}")
      for (el in timeElements.take(10)) {
          println("  Tag: ${el.tagName()}, Class: ${el.className()}, Text: ${el.text()}")
      }
      
      // Let's write the whole body HTML to a file so we can view_file it if needed!
      val bodyHtml = doc.body().html()
      java.io.File("app_body_temp.html").writeText(bodyHtml)
      println("Saved HTML body to app_body_temp.html")
    } catch (e: Exception) {
      e.printStackTrace()
      fail(e.message)
    }
  }

  @Test
  fun testStreamUrl() {
    try {
      // Setup global bypass for invalid SSL certificates
      val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
          object : javax.net.ssl.X509TrustManager {
              override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
              override fun checkClientTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
              override fun checkServerTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
          }
      )
      val sc = javax.net.ssl.SSLContext.getInstance("SSL")
      sc.init(null, trustAllCerts, java.security.SecureRandom())
      javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
      javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }

      println("--- FETCHING TV STREAM URL ---")
      val url = java.net.URL("https://tv.gialaitv.vn/tv.m3u8")
      val connection = url.openConnection() as java.net.HttpURLConnection
      connection.requestMethod = "GET"
      connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
      connection.connect()
      
      val code = connection.responseCode
      println("HTTP RESPONSE CODE: $code")
      
      val headers = connection.headerFields
      println("--- HEADERS ---")
      for ((k, v) in headers) {
          println("$k: $v")
      }
      
      val content = connection.inputStream.bufferedReader().use { it.readText() }
      println("--- FIRST 500 CHARS OF PLAYLIST ---")
      println(content.take(500))
    } catch (e: Exception) {
      e.printStackTrace()
      fail(e.message)
    }
  }
}
