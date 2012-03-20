package de.tototec.sbuild;
//package de.tobiasroeser.jackage.sbuild
//
//class ExampleScript(implicit project: Project) {
//
//  Goal.registerSchemeHandler("mvn", new CmvnSchemeHandler(".cache/cmvn"))
//
//  val clean = Goal("clean") exec {
//    // remove target dir
//  }
//  val test = Goal("test")
//
//  val dep1 = Goal("mvn:groupId:artifactId:version")
//  val dep2 = Goal("mvn:groupId:artifactId:version;classifier=jdk15")
//
//  val test2 = Goal("test2")
//
//  // One pass compile dependencies
//  val compile = Goal("compile") dependsOn {
//    'clean ++ dep1 ++ dep2
//  } exec {
//    println("exec")
//  }
//
//  'clean dependsOn test
//
//  // Two pass compile dependencies
//  // TODO: decide what is better or needed
//  // How to declare sources
//  val classpathCompile = Goal("classpath:compile") dependsOn 'clean
//  val compile2 = Goal("compile2") dependsOn {
//    clean ++ classpathCompile
//  } exec {
//    println("exec")
//  }
//
//  Goal("compile:javac;source=1.5;sourcePath=")
//
//  Goal("default:clean")
//  Goal("default:clean") exec {
//    println("clean")
//  }
//
//  def loadMavenLifecycle {
//    Goal("default:clean") exec println("clean")
//    Goal("default:compile") dependsOn Goal("default:clean") exec println("compile")
//  }
//
//  loadMavenLifecycle
//
//  Goal("compile") dependsOn dep1 ++ dep2 ++ 'precompile
//
//  Goal("clean2") dependsOn 'clean
//
//  val releaseJar = Goal("target/release.jar") dependsOn 'package exec {
//    // create jar, expensive!!!
//  }
//
//  Goal("release") dependsOn releaseJar
//
//}
