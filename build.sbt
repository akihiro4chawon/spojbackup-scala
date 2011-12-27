seq(conscriptSettings :_*)

organization := "com.github.akihiro4chawon"

name := "SPOJBackup"

version := "0.1.1"

scalaVersion := "2.9.1"

libraryDependencies += "net.sourceforge.htmlunit" % "htmlunit" % "2.9"

libraryDependencies += "com.github.scopt" %% "scopt" % "1.1.3"

publishTo <<= (version) {
  version: String =>
    def repo(name: String) = Resolver.file("file", new File("../akihiro4chawon.github.com/maven-repo") / name)
    val isSnapshot = version.trim.endsWith("SNAPSHOT")
    val repoName   = if (isSnapshot) "snapshots" else "releases"
    Some(repo(repoName))
}

