package de.tototec.sbuild

/**
 * ==SBuild Ant Support and Ant Wrappers Reference==
 *
 * This is the API Reference for SBuild Ant Support and the Ant Wrappers.
 *
 * This version is designed to be used with SBuild 0.4.0.
 *
 * Project Homepage: [[http://sbuild.tototec.de/]]
 *
 * SBuild Ant Support and Wrappers are released under the [[http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0]].
 *
 * ===How does SBuild's Ant Integration works?===
 *
 * SBuild has special support for Apache Ant and it's Ant Tasks.
 *
 * From a technical standpoint, Ant tasks are nothing special but Java code and can be therefore very easy integrated into an SBuild build script.
 * But, most Ant tasks make use of less or more Ant specific classes and utilities, for which SBuild has special support.
 *
 * Ant and SBuild both have a very similar concept of a project.
 * Most Ant API, especially the tasks, need a reference to `[[org.apache.tools.ant.Project]]` to function correctly.
 * SBuild provides an already configured Ant project implementation: `[[AntProject]]`, which ensures well integrated execution of Ant API.
 * You can use `[[AntProject$.apply]]` to access an instance of this Ant project, whenever you need an Ant project in your SBuild project.
 *
 * All Ant wrappers provided by SBuild already set the `AntProject` into the wrapped object, when appropriate, so that in most cases, you should not need to use AntProject at all.
 * But if you need an Ant project, the `AntProject` should be set into the class or object in question before any other functionality of that object is used.
 *
 * ===Wrapped Ant API and tasks===
 *
 * To provide a seamless experience, the most needed Ant building blocks have SBuild specific wrappers, which exploit Scala's powerful syntax.
 * Those wrappers provide an API which feels almost exactly like the one which is it used in Ant XML.
 *
 * The most common Ant utilities are located in [[de.tototec.sbuild.ant this]] package. Wrapped Ant tasks can be found in package [[de.tototec.sbuild.ant.tasks]] and its sub packages.
 *
 * ===Using Ant API, without a wrapper===
 *
 * SBuild only provides Ant wrappers for the most common Ant tasks.
 * If you need to use Ant classes which do not have a wrapper for SBuild yet, the following example might help you to understand, how you can use such classes.
 * As you will see, it is very simple.
 *
 * The `[[de.tototec.sbuild.ant.tasks.AntDelete]]` task is a simple wrapped Ant task, to delete files or directories.
 * To write a "clean" target which uses the `AntDelete` task to delete a directory "target", you would do it as follows:
 *
 * {{{
 * Target("phony:clean") exec {
 *   AntDelete(dir = Path("target"))
 * }
 * }}}
 *
 * As you see, we defined a phony target with the name "clean" which has no dependencies and with will use `[[de.tototec.sbuild.ant.tasks.AntDelete]]` to delete the directory.
 * By the use of `[[de.tototec.sbuild.Path]]`, we ensure, that the required `[[java.io.File]]` is relative to the current project directory.
 *
 * But, you can also use the API of the `[[org.apache.tools.ant.taskdefs.Delete]]` task directly, e.g. like this:
 *
 * {{{
 * Target("phony:clean") exec {
 *   new org.apache.tools.taskdefs.Delete() {
 *     setProject(AntProject())
 *     setDir(Path("target"))
 *   }.execute
 * }
 * }}}
 *
 * First, we set the SBuild-specific Ant project instance `[[AntProject]]` into the newly created instance of `Delete`.
 * To actually run the task, we need to call the `execute` method.
 *
 * In the example above we inherited the `Delete` task, but this is of course not necessary.
 * You could also write it in a more Java fashioned style:
 *
 * {{{
 * Target("phony:clean") exec {
 *   val delete = new org.apache.tools.taskdefs.Delete()
 *   delete.etProject(AntProject())
 *   delete.setDir(Path("target"))
 *   delete.execute
 * }
 * }}}
 *
 * ===How to write your own wrappers===
 *
 * All Ant wrappers are normal scala classes that wrap the Ant task by inheriting them.
 * Each wrapper should have an constructor with named parameters, and each of these parameters should have an default argument,
 * so that is is always possible to set only the wanted attributes and leave the others out.
 * To execute the wrapper, the `execute` method must be called.
 * Per convention the `execute` method should only be called once in the lifecycle on an task.
 *
 * In the majority of cases, setting only a small set of attributes a tasks supports, is enough.
 * To make this common case even more easy to write, wrapper classes provide  a companion object with an `apply` method,
 * which should have exactly the same parameters (and default arguments) as the class constructor.
 * It will create an instance of the task and '''immediatly''' invokes the execute method of that task.
 * Those `apply` methods should not return the task instance.
 *
 * The result, when using the companion object `apply` method is an even more compact notation, were you can leave out the `new` and the `execute`.
 *
 * Whenever you need to configure the tasks in a more complex way, you can simply re-add the "new" and invoke the `execute` method after you are done with configuring the tasks.
 *
 */
package object ant {

}
