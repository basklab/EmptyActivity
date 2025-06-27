package com.example.emptyactivity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.draganddrop.toExternalTransferData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import java.io.File
import android.content.ClipData
import android.content.ClipDescription

@Serializable
data class Task(var description: String, var isDone: Boolean = false)

@Serializable
data class Project(val name: String, val tasks: MutableList<Task> = mutableStateListOf())

class TaskManager {
    val projects = mutableStateListOf<Project>()

    var selectedTaskIndex by mutableStateOf<Pair<Project, Int>?>(null)
        private set
    var editingTaskIndex by mutableStateOf<Pair<Project, Int>?>(null)
        private set

    private val storageFile = File("tasks.json")

    init {
        loadTasks()
        if (projects.isEmpty()) {
            projects.addAll(
                listOf(
                    Project(
                        "Today", mutableListOf(
                            Task("Finish quarterly report"),
                            Task("Respond to client emails"),
                            Task("Prepare for team meeting"),
                            Task("Review code for upcoming release"),
                            Task("Organize desk and clean workspace")
                        )
                    ), Project(
                        "Upcoming", mutableListOf(
                            Task("Draft project roadmap for next quarter"),
                            Task("Research tools for process automation"),
                            Task("Update team documentation"),
                            Task("Plan team-building activities"),
                            Task("Follow up on pending invoices"),
                            Task("Schedule performance reviews")
                        )
                    )
                )
            )
            saveTasks()
        }
        if (selectedTaskIndex == null) {
            projects.firstOrNull { it.tasks.isNotEmpty() }?.let { project ->
                selectedTaskIndex = project to 0
                if (editingTaskIndex == null) {
                    editingTaskIndex = project to 0
                }
            }
        }
    }

    private fun saveTasks() {
        try {
            val jsonString = Json.encodeToString(projects.toList())
            storageFile.writeText(jsonString)
        } catch (e: Exception) {
            println("Error saving tasks: ${e.message}")
        }
    }

    private fun loadTasks() {
        try {
            if (storageFile.exists() && storageFile.length() > 0) {
                val jsonString = storageFile.readText()
                if (jsonString.isNotBlank()) {
                    val loadedProjects = Json.decodeFromString<List<Project>>(jsonString)
                    projects.addAll(loadedProjects)
                }
            }
        } catch (e: Exception) {
            println("Error loading tasks: ${e.message}")
        }
    }

    fun selectNextTask() {
        selectedTaskIndex = getNextTask(selectedTaskIndex, projects)
    }

    fun selectPreviousTask() {
        selectedTaskIndex = getPreviousTask(selectedTaskIndex, projects)
    }

    fun startEditingSelectedTask() {
        selectedTaskIndex?.let { editingTaskIndex = it }
    }

    fun startEditingTask(project: Project, index: Int) {
        editingTaskIndex = project to index
    }

    fun saveEditingTask(newDescription: String? = null) {
        editingTaskIndex?.let { (project, index) ->
            if (newDescription != null) {
                project.tasks[index].description = newDescription
            }
        }
        editingTaskIndex = null
        saveTasks()
    }

    fun toggleTaskDone(task: Task) {
        task.isDone = !task.isDone
        saveTasks()
    }

    fun addTask(project: Project, taskDescription: String) {
        project.tasks.add(Task(taskDescription))
        saveTasks()
    }

    fun removeTask(project: Project, task: Task) {
        val idx = project.tasks.indexOf(task)
        project.tasks.removeAt(idx)
        if (selectedTaskIndex?.first == project && selectedTaskIndex?.second == idx) {
            selectedTaskIndex = getPreviousTask(selectedTaskIndex, projects)
        }
        if (editingTaskIndex?.first == project && editingTaskIndex?.second == idx) {
            editingTaskIndex = null
        }
        saveTasks()
    }

    /** New: Reorder a task within its project and adjust selection/editing indices */
    fun moveTask(project: Project, taskToMove: Task, targetIndex: Int) {
        val list = project.tasks
        val initialIndex = list.indexOf(taskToMove)
        if (initialIndex == -1 || initialIndex == targetIndex || targetIndex < 0 || targetIndex > list.size) return

        if (initialIndex < targetIndex) {
            list.add(targetIndex, taskToMove)
            list.removeAt(initialIndex)
        } else {
            list.removeAt(initialIndex)
            list.add(targetIndex, taskToMove)
        }

        // Adjust selectedTaskIndex if needed
        selectedTaskIndex?.takeIf { it.first == project }?.let { (_, selIdx) ->
            selectedTaskIndex = when {
                selIdx == initialIndex -> project to targetIndex
                initialIndex < selIdx && targetIndex >= selIdx -> project to selIdx - 1
                initialIndex > selIdx && targetIndex <= selIdx -> project to selIdx + 1
                else -> project to selIdx
            }
        }

        // Adjust editingTaskIndex if needed
        editingTaskIndex?.takeIf { it.first == project }?.let { (_, editIdx) ->
            editingTaskIndex = when {
                editIdx == initialIndex -> project to targetIndex
                initialIndex < editIdx && targetIndex >= editIdx -> project to editIdx - 1
                initialIndex > editIdx && targetIndex <= editIdx -> project to editIdx + 1
                else -> project to editIdx
            }
        }

        saveTasks()
    }

    private fun getNextTask(
        current: Pair<Project, Int>?,
        projectsList: List<Project>
    ): Pair<Project, Int>? {
        val nonEmpty = projectsList.filter { it.tasks.isNotEmpty() }
        if (nonEmpty.isEmpty()) return null
        if (current == null) return nonEmpty.first() to 0
        val (proj, idx) = current
        val pi = nonEmpty.indexOf(proj)
        return when {
            idx + 1 < proj.tasks.size -> proj to idx + 1
            pi + 1 < nonEmpty.size -> nonEmpty[pi + 1] to 0
            else -> nonEmpty.first() to 0
        }
    }

    private fun getPreviousTask(
        current: Pair<Project, Int>?,
        projectsList: List<Project>
    ): Pair<Project, Int>? {
        val nonEmpty = projectsList.filter { it.tasks.isNotEmpty() }
        if (nonEmpty.isEmpty()) return null
        if (current == null) {
            val lastProj = nonEmpty.last()
            return lastProj to lastProj.tasks.lastIndex
        }
        val (proj, idx) = current
        val pi = nonEmpty.indexOf(proj)
        return when {
            idx - 1 >= 0 -> proj to idx - 1
            pi - 1 >= 0 -> nonEmpty[pi - 1].let { it to it.tasks.lastIndex }
            else -> nonEmpty.last().let { it to it.tasks.lastIndex }
        }
    }
}

@Composable
fun App() {
    val taskManager = remember { TaskManager() }

    IntUiTheme(isDark = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(16.dp)
                .onKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionDown -> {
                            taskManager.selectNextTask(); true
                        }

                        Key.DirectionUp -> {
                            taskManager.selectPreviousTask(); true
                        }

                        Key.Enter -> {
                            if (taskManager.editingTaskIndex == null && taskManager.selectedTaskIndex != null) {
                                taskManager.startEditingSelectedTask()
                            }
                            true
                        }

                        else -> false
                    }
                }
        ) {
            taskManager.projects.forEach { project ->
                ProjectSection(
                    project = project,
                    selectedTaskIndex = taskManager.selectedTaskIndex,
                    editingTaskIndex = taskManager.editingTaskIndex,
                    onEditClick = { p, i -> taskManager.startEditingTask(p, i) },
                    onSaveClick = { desc -> taskManager.saveEditingTask(desc) },
                    onToggleDone = { t -> taskManager.toggleTaskDone(t) },
                    onMoveTask = { p, t, idx -> taskManager.moveTask(p, t, idx) }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ProjectSection(
    project: Project,
    selectedTaskIndex: Pair<Project, Int>?,
    editingTaskIndex: Pair<Project, Int>?,
    onEditClick: (Project, Int) -> Unit,
    onSaveClick: (String?) -> Unit,
    onToggleDone: (Task) -> Unit,
    onMoveTask: (Project, Task, Int) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("<star>")
            Spacer(modifier = Modifier.width(8.dp))
            Text(project.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))

        project.tasks.forEachIndexed { index, task ->
            var isBeingDraggedOver by remember { mutableStateOf(false) }
            var isDragging by remember { mutableStateOf(false) }
            var dragOffset by remember { mutableStateOf(Offset.Zero) }

            TaskItem(
                task = task,
                isEditing = editingTaskIndex == project to index,
                isSelected = selectedTaskIndex == project to index,
                onEditClick = { onEditClick(project, index) },
                onSaveClick = onSaveClick,
                onToggleDone = { onToggleDone(task) },
                modifier = dragAndDropSource(
                    drawDragDecoration = {
                        if (isDragging) {
                            Column(
                                modifier = Modifier.graphicsLayer(
                                    alpha = 0.7f,
                                    translationX = dragOffset.x,
                                    translationY = dragOffset.y
                                )
                            ) {
                                TaskItemContent(task, false, false, {}, {}, {})
                            }
                        }
                    }
                ) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            dragOffset = Offset.Zero
                        },
                        onDragCancel = {
                            isDragging = false
                            dragOffset = Offset.Zero
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            dragOffset += delta
                        }
                    )
                    toExternalTransferData(
                        ClipData(
                            ClipDescription("task", arrayOf("text/plain")),
                            ClipData.Item("${project.name}/${task.description}")
                        )
                    )
                }
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { it.mimeTypes.any { mt -> mt == "text/plain" } },
                        onDrop = { event ->
                            val dropped = event.clipData.getItemAt(0).text.toString()
                            val (srcProj, desc) = dropped.split('/', limit = 2)
                            if (srcProj == project.name) {
                                project.tasks.find { it.description == desc }?.let { taskToMove ->
                                    onMoveTask(project, taskToMove, index)
                                    true
                                } ?: false
                            } else false
                        },
                        onDragEnter = { isBeingDraggedOver = true },
                        onDragExit = { isBeingDraggedOver = false }
                    )
                    .background(
                        if (isBeingDraggedOver) Color.Gray.copy(alpha = 0.5f)
                        else Color.Transparent
                    )
            )
        }
    }
}

@Composable
fun TaskItem(
    task: Task,
    isEditing: Boolean,
    isSelected: Boolean,
    onEditClick: () -> Unit,
    onSaveClick: (String?) -> Unit,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(if (isSelected && !isEditing) Color.DarkGray else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TaskItemContent(task, isEditing, isSelected, onEditClick, onSaveClick, onToggleDone)
    }
}

@Composable
fun TaskItemContent(
    task: Task,
    isEditing: Boolean,
    isSelected: Boolean,
    onEditClick: () -> Unit,
    onSaveClick: (String?) -> Unit,
    onToggleDone: () -> Unit
) {
    Checkbox(
        checked = task.isDone,
        onCheckedChange = { onToggleDone() }
    )
    Spacer(Modifier.width(8.dp))

    if (isEditing) {
        val state = rememberTextFieldState(task.description)
        TextField(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onKeyEvent {
                    when (it.key) {
                        Key.Enter -> {
                            onSaveClick(state.text); true
                        }

                        Key.Escape -> {
                            onSaveClick(null); true
                        }

                        else -> false
                    }
                }
        )
        Spacer(Modifier.width(8.dp))
        Text("Save", Modifier.clickable { onSaveClick(state.text) }, color = Color.Blue)
    } else {
        Text(
            task.description,
            fontSize = 16.sp,
            modifier = Modifier
                .weight(1f)
                .clickable { if (isSelected) onEditClick() }
        )
        Spacer(Modifier.width(8.dp))
        Text("Edit", Modifier.clickable { onEditClick() }, color = Color.Blue)
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "To-Do App") {
        App()
    }
}
