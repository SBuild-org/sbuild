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
import org.eclipse.swt.events.SelectionListener
import org.eclipse.jface.viewers.ViewerColumn
import org.eclipse.jface.viewers.CellLabelProvider
import org.eclipse.jface.viewers.ColumnViewer
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.jface.viewers.TreeViewerColumn
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.jface.viewers.ColumnLabelProvider
import org.eclipse.jface.viewers.ArrayContentProvider
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.SelectionChangedEvent

object ViewerColumnBuilder {
  def apply(header: String = null,
            width: java.lang.Integer = null,
            style: java.lang.Integer = null,
            labelProvider: CellLabelProvider = null,
            resizable: java.lang.Boolean = null,
            moveable: java.lang.Boolean = null,
            viewer: ColumnViewer = null): ViewerColumn = viewer match {

    case tableViewer: TableViewer =>
      val column = new TableViewerColumn(tableViewer, if (style == null) SWT.NONE else style.intValue)
      if (header != null) column.getColumn.setText(header)
      if (width != null) column.getColumn.setWidth(width.intValue)
      if (labelProvider != null) column.setLabelProvider(labelProvider)
      else column.setLabelProvider(new ColumnLabelProvider() {
        override def getText(element: Object): String = ""
      })
      if (resizable != null) column.getColumn.setResizable(resizable.booleanValue)
      if (moveable != null) column.getColumn.setMoveable(moveable.booleanValue)
      column

    case treeViewer: TreeViewer =>
      val column = new TreeViewerColumn(treeViewer, if (style == null) SWT.NONE else style.intValue)
      if (header != null) column.getColumn.setText(header)
      if (width != null) column.getColumn.setWidth(width.intValue)
      if (labelProvider != null) column.setLabelProvider(labelProvider)
      else column.setLabelProvider(new ColumnLabelProvider() {
        override def getText(element: Object): String = ""
      })
      if (resizable != null) column.getColumn.setResizable(resizable.booleanValue)
      if (moveable != null) column.getColumn.setMoveable(moveable.booleanValue)
      column

    case _ =>
      throw new RuntimeException("Unsupported viewer")
  }
}

class SBuildClasspathContainerPage extends WizardPage("SBuild Libraries") with IClasspathContainerPage with IClasspathContainerPageExtension {

  object AliasEntry {
    def apply(key: String, value: String) = new AliasEntry(key, value)
    def unapply(e: AliasEntry): Option[(String, String)] = Some(e.key, e.value)
  }
  class AliasEntry(var key: String, var value: String)

  val containerPath = SBuildClasspathContainer.ContainerName

  private var project: IJavaProject = _
  private var options: Map[String, String] = Map()
  private val settings: Settings = new Settings

  setDescription("Configure SBuild Libraries")
  setPageComplete(true)

  var aliasModel: Seq[AliasEntry] = Seq()

  override def initialize(project: IJavaProject, currentEntries: Array[IClasspathEntry]) = {
    this.project = project
    debug("Read workspace project aliases into " + getClass())
    aliasModel = WorkspaceProjectAliases.read(project).toSeq.map { case (key, value) => new AliasEntry(key, value) }
  }

  override def setSelection(classpathEntry: IClasspathEntry) = settings.fromIClasspathEntry(classpathEntry)
  override def getSelection: IClasspathEntry = settings.toIClasspathEntry

  override def finish: Boolean = {
    debug("Write workspace project aliases from " + getClass())
    WorkspaceProjectAliases.write(project, aliasModel.map { case AliasEntry(key, value) => (key, value) }.toMap)
    true
  }

  override def createControl(parent: Composite) {

    val composite = new PageComposite(parent, SWT.NONE)
    composite.setLayoutData(new GridData(SWT.BEGINNING | SWT.TOP));

    val sbuildFile = composite.sbuildFileText
    sbuildFile.addModifyListener(new ModifyListener {
      override def modifyText(e: ModifyEvent) {
        settings.sbuildFile = sbuildFile.getText
      }
    })
    sbuildFile.setText(settings.sbuildFile)

    val exportedClasspath = composite.exportedClasspathText
    exportedClasspath.addModifyListener(new ModifyListener {
      override def modifyText(e: ModifyEvent) {
        settings.exportedClasspath = exportedClasspath.getText
      }
    })
    exportedClasspath.setText(settings.exportedClasspath)

    val updateDependenciesButton = composite.updateDependenciesButton
    updateDependenciesButton.setSelection(settings.relaxedFetchOfDependencies)
    updateDependenciesButton.addSelectionListener(new SelectionListener() {
      override def widgetSelected(event: SelectionEvent) =
        settings.relaxedFetchOfDependencies = updateDependenciesButton.getSelection
      override def widgetDefaultSelected(event: SelectionEvent) =
        settings.relaxedFetchOfDependencies = updateDependenciesButton.getSelection
    })

    val workspaceProjectAliases = composite.workspaceProjectAliasTable
    ViewerColumnBuilder(viewer = workspaceProjectAliases, header = "Dependency", width = 200,
      labelProvider = new ColumnLabelProvider() {
        override def getText(element: Object) = element match {
          case AliasEntry(key: String, value) => key
          case _ => ""
        }
      })
    ViewerColumnBuilder(viewer = workspaceProjectAliases, header = "Project in Workspace", width = 200,
      labelProvider = new ColumnLabelProvider() {
        override def getText(element: Object) = element match {
          case AliasEntry(key, value: String) => value
          case _ => ""
        }
      })

    val delButton = composite.removeAliasButton
    delButton.setEnabled(false)

    workspaceProjectAliases.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) {
        val delEnabled = event.getSelection match {
          case sel: IStructuredSelection if !sel.isEmpty => true
          case _ => false
        }
        delButton.setEnabled(delEnabled)
      }
    })

    workspaceProjectAliases.setContentProvider(new ArrayContentProvider())

    workspaceProjectAliases.setInput(aliasModel.toArray)

    composite.addAliasButton.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(event: SelectionEvent) {
        aliasModel ++= Seq(AliasEntry("", ""))
        workspaceProjectAliases.setInput(aliasModel.toArray)
      }
    })
    delButton.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(event: SelectionEvent) {
        workspaceProjectAliases.getSelection match {
          case sel: IStructuredSelection if !sel.isEmpty =>
            sel.getFirstElement match {
              case entry: AliasEntry =>
                aliasModel = aliasModel.filter(_ != entry)
                workspaceProjectAliases.setInput(aliasModel.toArray)
            }
          case _ =>
        }
      }
    })

    //    val workspaceResolution = new Button(composite, SWT.CHECK);
    //    workspaceResolution.setText("Resolve dependencies from Workspace")
    //    workspaceResolution.setSelection(settings.workspaceResolution)
    //    workspaceResolution.addSelectionListener(new SelectionAdapter() {
    //      override def widgetSelected(event: SelectionEvent) {
    //        settings.workspaceResolution = workspaceResolution.getSelection
    //      }
    //      override def widgetDefaultSelected(event: SelectionEvent) = widgetSelected(event)
    //    })

    setControl(composite)
  }

}