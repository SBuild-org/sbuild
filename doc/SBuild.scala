import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._

@version("0.3.2")
@include("../SBuildConfig.scala")
@classpath(
  "mvn:org.apache.ant:ant:1.8.4",
  "zip:file=plugins/org.eclipse.mylyn.wikitext.core_1.7.2.v20120916-1200.jar;archive=http://mirror.netcologne.de/eclipse//mylyn/drops/3.8.2/v20120916-1200/mylyn-3.8.2.v20120916-1200.zip",
  "zip:file=plugins/org.eclipse.mylyn.wikitext.textile.core_1.7.2.v20120916-1200.jar;archive=http://mirror.netcologne.de/eclipse//mylyn/drops/3.8.2/v20120916-1200/mylyn-3.8.2.v20120916-1200.zip"
)
class SBuild(implicit _project: Project) {

  Target("phony:clean") exec {
    AntDelete(dir = Path("target"))
  }

  Target("phony:all") dependsOn "convert-wiki-to-html" ~ "convert-manual-to-html" ~ "convert-manual-to-docbook"

  // TODO: depend on wiki files
  Target("phony:convert-wiki-to-html") exec {
    AntCopy(fileSet = AntFileSet(dir = Path("wiki")), toDir = Path("target/wiki"))

    // Preprocess wiki files converting internal links to external ones.
    val files = Path("target/wiki").listFiles.filter(f => f.getName.endsWith(".textile"))
    resolveTextile(files = files, includeSearchPath = Seq(Path("wiki")), linkPattern = "{0}.html")

    // Convert wiki files
    new org.eclipse.mylyn.wikitext.core.util.anttask.MarkupToHtmlTask() {
      setProject(AntProject())
      setMarkupLanguage("Textile")
      addFileset(AntFileSet(dir = Path("target/wiki")))
      setSourceEncoding("UTF-8")
    }.execute

  }

  // TODO: depend on wiki files
  Target("phony:convert-manual-to-html") exec {
    AntCopy(fileSet = AntFileSet(dir = Path("manual")), toDir = Path("target/manual"))

    // Preprocess wiki files converting internal links to external ones.
    val files = Path("target/manual").listFiles.filter(f => f.getName.endsWith(".textile"))
    resolveTextile(files = files, includeSearchPath = Seq(Path("manual")), linkPattern = "{0}.html")

    val stylesheet = new org.eclipse.mylyn.wikitext.core.util.anttask.MarkupToHtmlTask.Stylesheet()
    //    stylesheet.setStylesheet(readFile(Path("manual/style.css")))
    stylesheet.setFile(Path("manual/style.css"))

    // Convert wiki files
    new org.eclipse.mylyn.wikitext.core.util.anttask.MarkupToHtmlTask() {
      setProject(AntProject())
      setMarkupLanguage("Textile")
      addFileset(AntFileSet(dir = Path("target/manual")))
      setSourceEncoding("UTF-8")
      addStylesheet(stylesheet)
    }.execute

  }

  // TODO: depend on wiki files
  Target("convert-manual-to-docbook") exec {
    AntCopy(fileSet = AntFileSet(dir = Path("manual")), toDir = Path("target/manual"))

    // Preprocess wiki files converting internal links to external ones.
    val files = Path("target/manual").listFiles.filter(f => f.getName.endsWith(".textile"))
    resolveTextile(files = files, includeSearchPath = Seq(Path("manual")), linkPattern = "{0}.html")

    // Convert wiki files
    new org.eclipse.mylyn.wikitext.core.util.anttask.MarkupToDocbookTask() {
      setProject(AntProject())
      setMarkupLanguage("Textile")
      //      addFileset(AntFileSet(file = Path("target/manual")))
      setFile(Path("target/manual/ReferenceManual.textile"))
      setSourceEncoding("UTF-8")
    }.execute

  }

  Target("convert-docbook-to-html") dependsOn "convert-manual-to-docbook" exec {
    new org.apache.tools.ant.taskdefs.XSLTProcess() {
      setProject(AntProject())
      setIn(Path("target/manual/ReferenceManual.xml"))
      setExtension("xml")
      setOut(Path("target/manual/ReferenceManual.fo"))

    }.execute
  }

  // Helper for textile

  def resolveTextile(files: Seq[java.io.File], includeSearchPath: Seq[java.io.File], linkPattern: String) = {
    val link = new java.text.MessageFormat(linkPattern)
    files.foreach { file =>
      println("Resolving textile file: " + file)

      val fileName = file.getName match {
        case n if n.toLowerCase.endsWith(".textile") =>
          n.substring(0, n.length - 8)
        case n => n
      }

      var content = readFile(file)

      val includes = resolveIncludes(content, includeSearchPath)
      content = includes._1
      val names = includes._2

      content = resolveLocalLinks(content, linkPattern, names.map(name => name -> fileName).toMap)

      val writer = new java.io.FileWriter(file)
      writer.write(content)
      writer.close
    }
  }

  val internalLinkWithName = """\[\[([^|#]+?)(#.*?)([|](.+?))?\]\]""".r

  // TODO: handle #-refs
  def resolveLocalLinks(content: String, linkPattern: String, aliasMap: Map[String, String]): String = {
    val link = new java.text.MessageFormat(linkPattern)

    def target(name: String): String = aliasMap.get(name) match {
      case None => name
      case Some(name) => name
    }

    internalLinkWithName.replaceAllIn(content, m => {
      val mLink = m.group(1)
      val mInnerRef = m.group(2) match {
        case ref if ref != null && ref.trim.length > 1 => ref
        case _ => ""
      }
      val mTitle = m.group(4) match {
        case t if t != null && t.trim.length > 0 => t
        case _ => mLink
      }

      java.util.regex.Matcher.quoteReplacement(
        s""""${mTitle}":${link.format(Array(target(mLink)))}${mInnerRef}"""
      )

    })
  }

  def readFile(file: java.io.File): String = {
    if (!file.exists) throw new java.io.FileNotFoundException("Could not found file: " + file)
    val source = scala.io.Source.fromFile(file)
    val content = source.getLines.mkString("\n")
    source.close
    content
  }

  val include = """\{\{include\((.+?)\)\}\}""".r

  // TODO: replace links to included-files into self-links
  /**
   * @return A Pair _1: containing the included content, _2: a map containing included file names
   */
  def resolveIncludes(content: String, searchPath: Seq[java.io.File]): (String, Seq[String]) = {
    var names: Seq[String] = Seq()

    val newContent = include.replaceAllIn(content, m => {
      val name = m.group(1)
      names ++= Seq(name)
      val part = name + ".textile"
      println("Including document: " + part)

      var includePath = searchPath.find {
        path => Path(path.getPath, part).exists
      }.map {
        path => Path(path.getPath, part)
      }

      includePath match {
        case None =>
          throw new java.io.FileNotFoundException("Could not found file: " + part)
        case Some(file) =>
          val included = readFile(file)
          val result = if (include.findFirstIn(included).isDefined) {
            val (newContent, newNames) = resolveIncludes(included, searchPath)
            names ++= newNames
            newContent
          } else {
            included
          }
          java.util.regex.Matcher.quoteReplacement(result)
      }
    })

    (newContent, names)
  }

}
