package de.tototec.sbuild.eclipse.plugin

import org.eclipse.jface.wizard.WizardPage
import org.eclipse.jdt.ui.wizards.IClasspathContainerPage
import org.eclipse.jdt.ui.wizards.IClasspathContainerPageExtension
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.widgets.Text
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.events.ModifyEvent

class SBuildClasspathContainerPage extends WizardPage("SBuild Libraries") with IClasspathContainerPage with IClasspathContainerPageExtension {

  val containerPath = SBuildClasspathContainer.ContainerName

  private var project: IJavaProject = _
  private var options: Map[String, String] = Map()
  private val settings: Settings = new Settings

  setDescription("Configure SBuild Libraries")
  setPageComplete(true)

  override def initialize(project: IJavaProject, currentEntries: Array[IClasspathEntry]) =
    this.project = project

  override def setSelection(classpathEntry: IClasspathEntry) = settings.fromIClasspathEntry(classpathEntry)
  override def getSelection: IClasspathEntry = settings.toIClasspathEntry

  override def finish: Boolean = true

  override def createControl(parent: Composite) {
    val composite = new Composite(parent, SWT.NONE)
    composite.setLayout(new GridLayout(2, false))
    composite.setLayoutData(new GridData(SWT.BEGINNING | SWT.TOP));
    composite.setFont(parent.getFont())

    //    new Label(composite, SWT.NONE).setText("Workspace resolution")

    //    val workspaceResolution = new Button(composite, SWT.CHECK);
    //    workspaceResolution.setText("Resolve dependencies from Workspace")
    //    workspaceResolution.setSelection(settings.workspaceResolution)
    //    workspaceResolution.addSelectionListener(new SelectionAdapter() {
    //      override def widgetSelected(event: SelectionEvent) {
    //        settings.workspaceResolution = workspaceResolution.getSelection
    //      }
    //      override def widgetDefaultSelected(event: SelectionEvent) = widgetSelected(event)
    //    })

    new Label(composite, SWT.NONE).setText("Buildfile (default: SBuild.scala)")

    val sbuildFile = new Text(composite, SWT.BORDER)
    sbuildFile.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))
    sbuildFile.addModifyListener(new ModifyListener {
      override def modifyText(e: ModifyEvent) {
        settings.sbuildFile = sbuildFile.getText
      }
    })
    sbuildFile.setText(settings.sbuildFile)

    new Label(composite, SWT.NONE).setText("Exported Classpath (default: eclipse.classpath)")

    val exportedClasspath = new Text(composite, SWT.BORDER)
    exportedClasspath.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))
    exportedClasspath.addModifyListener(new ModifyListener {
      override def modifyText(e: ModifyEvent) {
        settings.exportedClasspath = exportedClasspath.getText
      }
    })
    exportedClasspath.setText(settings.exportedClasspath)

    setControl(composite)
  }

}