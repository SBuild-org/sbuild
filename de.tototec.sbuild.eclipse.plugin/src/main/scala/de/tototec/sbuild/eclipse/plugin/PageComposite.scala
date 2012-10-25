package de.tototec.sbuild.eclipse.plugin

import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Text
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Button
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.swt.widgets.Group

class PageComposite(parent: Composite, style: Int) extends Composite(parent, style) {

  setLayout(new GridLayout(2, false))
  setFont(parent.getFont)

  new Label(this, SWT.NONE).setText("Buildfile (default: SBuild.scala)")
  val sbuildFileText = new Text(this, SWT.BORDER)
  sbuildFileText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))

  new Label(this, SWT.NONE).setText("Exported Classpath (default: eclipse.classpath)")
  val exportedClasspathText = new Text(this, SWT.BORDER)
  exportedClasspathText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))

  new Label(this, SWT.NONE).setText("Update of dependencies")
  val updateDependenciesButton = new Button(this, SWT.CHECK)
  updateDependenciesButton.setText("Resolve/download only missing dependencies")

  private val aliasGroup = new Group(this, SWT.NONE)
  aliasGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1))
  aliasGroup.setLayout(new GridLayout(2, false))
  aliasGroup.setText("Workspace Project Aliases")
  
  val workspaceProjectAliasTable = new TableViewer(aliasGroup, SWT.BORDER)
  workspaceProjectAliasTable.getTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 3))
  workspaceProjectAliasTable.getTable.setHeaderVisible(true)
  
  val addAliasButton = new Button(aliasGroup, SWT.PUSH)
  addAliasButton.setText("Add")
  addAliasButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false))
  val removeAliasButton = new Button(aliasGroup, SWT.PUSH)
  removeAliasButton.setText("Remove")
  removeAliasButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false))
  
}