package de.tototec.sbuild.ant

/**
 * Various wrapper classes and objects around common Ant Tasks. All wrappers start with the prefix "Ant" followed by the original task name.
 * Usually, for each wrapped task there is an apply-factory method that immediately executes the task. 
 * See in the following example:
 * <pre><code>
 * Target("phony:hello-ant") exec {
 *   val antEcho = new org.apache.tools.ant.taskdefs.Echo()
 *   antEcho.setMessage("Hello Ant!")
 *   antEcho.execute
 * }
 * 
 * Target("phony:hello-ant-wrapper") exec {
 *   val helloEcho = new AntEcho(message = "Hello!")
 *   helloEcho.execute
 * }
 * 
 * Target("phony:hello-ant-wrapper-object") exec {
 *   AntEcho(message = "Hello!")
 * }
 * </code></pre>  
 * 
 * The first target "hello-ant" just uses the standard ant task in a programmatic way.
 * The second target "hello-ant-wrapper" does the same but uses the AntEcho wrapper class, which provides a lot of it properties as named constructor arguments.
 * The third target "hello-ant-wrapper-object" results in a very short and easy the write/read version by using the AntEcho wrapper object.
 */
package tasks {}

