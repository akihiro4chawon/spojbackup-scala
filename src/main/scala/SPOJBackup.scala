package com.github.akihiro4chawon

import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.BrowserVersion
import com.gargoylesoftware.htmlunit.html.HtmlForm
import com.gargoylesoftware.htmlunit.html.HtmlButtonInput
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput
import com.gargoylesoftware.htmlunit.html.HtmlPage
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput
import com.gargoylesoftware.htmlunit.html.HtmlTextInput
import com.gargoylesoftware.htmlunit.ProxyConfig
import com.gargoylesoftware.htmlunit.TextPage
import com.gargoylesoftware.htmlunit.UnexpectedPage

import java.io.File

import scala.collection.JavaConversions._

/** The launched conscript entry point */
class SPOJBackup extends xsbti.AppMain {
  def run(config: xsbti.AppConfiguration) = {
    Exit(SPOJBackup.run(config.arguments))
  }
}

object SPOJBackup extends HtmlUnitImplicits {
  import ConfigManager.{username, password, outputPath}

  /** Shared by the launched version and the runnable version,
   * returns the process status code */
  def run(args: Array[String]): Int = {
    val parser = new scopt.OptionParser("spojbackup") {
      opt("o", "outputpath", "Destination directory to store solutions", ConfigManager.outputPath_=_)
      opt("u", "username", "Your username on SPOJ", ConfigManager.username_=_)
      opt("p", "password", "Your password on SPOJ", ConfigManager.password_=_)
    }
    
    if (parser.parse(args)) {
      ConfigManager.confirm()
      downloadSolutions(getSolutions)
      0
    } else {
      -1
    }
  }
  
  /** Standard runnable class entrypoint */
  def main(args: Array[String]) {
    System.exit(run(args))
  }
  
  val webClient = new WebClient(BrowserVersion.FIREFOX_3_6)
  getDefaultProxy foreach { webClient.setProxyConfig }
  webClient.setJavaScriptEnabled(false)
  
  def getSolutions = {
    // authenticate the user
    println("Authenticating "+username)
    val page = webClient.getPage[HtmlPage]("http://spoj.pl")
    val form = page.getFormByName("login")
    form.getInputByName[HtmlTextInput]("login_user").setValueAttribute(username)
    form.getInputByName[HtmlPasswordInput]("password").setValueAttribute(password)
    // sign in for a day to avoid timeouts
    form.getInputByName[HtmlCheckBoxInput]("autologin").setChecked(true)
    val response = form.getInputByName[HtmlButtonInput]("submit").click[HtmlPage]()
    
    if (response.toString.indexOf("Authentication failed!") != -1) {
      println("Error authenticating - " + username)
      println(response.toString)
      exit(0)
    }
    
    // grab the signed submissions list
    println("Grabbing siglist for " + username)
    val siglist = webClient.getPage[TextPage]("http://www.spoj.pl/status/" + username + "/signedlist")
    
    // dump first nine useless lines in signed list for formatting
    // and make a list of all AC's and challenges
    val footer = "\\------------------------------------------------------------------------------/"
    val mysublist = for {
      line <- siglist.getContent.lines.drop(9).takeWhile(footer != )
      entry = line split '|' map {_.trim}
      result = entry(4)
      if result == "AC" || result.forall{_.isDigit}
    } yield entry
    
    println("Done !!!")
    mysublist.toList
  }
  
  def downloadSolutions(mysublist: Seq[Array[String]]) = {
    val totalSubmissions = mysublist.length
    
    println("Fetching sources into " + outputPath)
    
    val SourceFilename = "([a-zA-Z0-9]+)-([0-9]+).*".r
    val existingFiles = new File(outputPath).listFiles
                          .map {_.getName}
                          .collect {case SourceFilename(prob, id) => (id, prob)}
                          .toMap

    for ((entry, progress) <- mysublist zip Stream.from(1)) {
      val id = entry(1)
      val problem = entry(3)
      if (existingFiles.get(id) == Some(problem)) {
        println(progress+"/"+totalSubmissions+" - "+problem+" skipped.") 
      } else { 
        val sourceUrl = "http://www.spoj.pl/files/src/save/"+entry(1)
        val sourceCode = webClient.getPage[UnexpectedPage](sourceUrl)
        val headers = sourceCode.getWebResponse.getResponseHeaders
                        .map{pair => pair: (String, String)}.toMap
        val cd = headers.get("Content-Disposition").flatMap{_.split('=').lift(1)}.getOrElse(entry(1))
        val filename = problem + "-" + cd
        
        import java.io.{File, FileOutputStream}
        val os = new FileOutputStream(new File(outputPath+"/"+filename))
        val is = sourceCode.getInputStream()
        val buf = new Array[Byte](65534)
        var size = 0
        while ({size = is.read(buf); size != -1})
          os.write(buf, 0, size)
        os.close()
        println(progress+"/"+totalSubmissions+" - "+filename+" done.")
      }
    }
    println("Created a backup of "+totalSubmissions+" submission for "+username)
  }
  
  def getDefaultProxy: Option[ProxyConfig] = {
    def systemProperty = for {
      host <- Option(System.getProperty("http.proxyHost"))
      // TODO: catch java.lang.NumberFormatException
      port <- Option(System.getProperty("http.proxyPort")) map {Integer.parseInt} orElse Some(8080)
    } yield new ProxyConfig(host, port)
    
    def environmentVariable = {
      //val addrExpr = """(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})"""
      val octetExpr = "(?:[0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])"
      val addrExpr = """(?:(?:"""+octetExpr+"""\.){3}"""+octetExpr+""")"""
      val subExpr = """(?:[a-zA-Z]|[a-zA-Z][a-zA-Z0-9\-]*[a-zA-Z0-9])"""
      val hostnameExpr = """(?:(?:"""+subExpr+"""\.)*"""+subExpr+""")"""
      val portExpr = """(?:\d+){1,5}"""
      val HttpProxy = ("http://("+addrExpr+"|"+hostnameExpr+")(?::("+portExpr+"))?").r
      
      for {
        HttpProxy(host, portStr) <- Option(System.getenv("http_proxy"))
        port = Integer.parseInt(portStr)
      } yield new ProxyConfig(host, port)
    }
    
    systemProperty orElse environmentVariable
  }
}

case class Exit(val code: Int) extends xsbti.Exit

trait HtmlUnitImplicits {
  import com.gargoylesoftware.htmlunit.util.NameValuePair
  implicit def nameValuePairToScalaTuple(p: NameValuePair): (String, String) =
    (p.getName(), p.getValue())
}

object ConfigManager {
  var password: String = null
  var username: String = null
  var proxyConfig: Option[ProxyConfig] = None
  var outputPath: String = "."//new File(".")
  
  def confirm() {
    if (username == null)
      username = Console.readLine("Enter your SPOJ username: ")
    if (password == null)
      password = Console.readLine("Enter your SPOJ password: ")
//      password = new String(System.console().readPassword("Enter your SPOJ password: "))
  }
}
