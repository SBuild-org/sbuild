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
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.ColumnLabelProvider
import org.eclipse.jface.viewers.ArrayContentProvider
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.EditingSupport
import org.eclipse.jface.viewers.TextCellEditor
import org.eclipse.jface.viewers.CellEditor

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

    val col1 = new TableViewerColumn(workspaceProjectAliases, SWT.LEFT)
    col1.getColumn.setText("Dependency")
    col1.getColumn.setWidth(200)
    col1.setLabelProvider(new ColumnLabelProvider() {
      override def getText(element: Object) = element match {
        case AliasEntry(key: String, value) => key
        case _ => ""
      }
    })

    val col1EditingSupport = new EditingSupport(workspaceProjectAliases) {
      override def canEdit(o: Object): Boolean = o.isInstanceOf[AliasEntry]
      override def getCellEditor(o: Object): CellEditor = new TextCellEditor(workspaceProjectAliases.getTable)
      override def getValue(o: Object) = o match {
        case AliasEntry(key, value) => key
        case _ => ""
      }
      override def setValue(o: Object, newVal: Object) = o match {
        case aliasEntry: AliasEntry if newVal.isInstanceOf[String] => 
          aliasEntry.key = newVal.asInstanceOf[String]
          workspaceProjectAliases.update(o, null)
        case _ =>
      }
    }
    col1.setEditingSupport(col1EditingSupport)

    val col2 = new TableViewerColumn(workspaceProjectAliases, SWT.LEFT)
    col2.getColumn.setText("Workspace Project")
    col2.getColumn.setWidth(200)
    col2.setLabelProvider(new ColumnLabelProvider() {
      override def getText(element: Object) = element match {
        case AliasEntry(key: String, value) => value
        case _ => ""
      }
    })

    val col2EditingSupport = new EditingSupport(workspaceProjectAliases) {
      override def canEdit(o: Object): Boolean = o.isInstanceOf[AliasEntry]
      override def getCellEditor(o: Object): CellEditor = new TextCellEditor(workspaceProjectAliases.getTable)
      override def getValue(o: Object) = o match {
        case AliasEntry(key, value) => value
        case _ => ""
      }
      override def setValue(o: Object, newVal: Object) = o match {
        case aliasEntry: AliasEntry if newVal.isInstanceOf[String] => 
          aliasEntry.value = newVal.asInstanceOf[String]
          workspaceProjectAliases.update(o, null)
        case _ =>
      }
    }
    col2.setEditingSupport(col2EditingSupport)

    
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

    setControl(composite)
  }

}