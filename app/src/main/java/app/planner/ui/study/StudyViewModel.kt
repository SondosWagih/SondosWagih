package app.planner.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.planner.data.local.entity.*
import app.planner.data.repository.StudyRepository
import app.planner.domain.model.*
import app.planner.domain.validation.ValidationError
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudyUiState(
    val courses: List<CourseEntity> = emptyList(),
    val events: List<StudyEventEntity> = emptyList(),
    val exams: List<ExamEntity> = emptyList(),
    val assignments: List<AssignmentEntity> = emptyList(),
    val sessions: List<StudySessionEntity> = emptyList(),
)

class StudyViewModel(private val repo: StudyRepository) : ViewModel() {
    val state: StateFlow<StudyUiState> =
        combine(repo.courses, repo.events, repo.exams, repo.assignments, repo.sessions) { c, e, x, a, s ->
            StudyUiState(c, e, x, a, s)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, StudyUiState())

    fun saveCourse(d: CourseDraft, onResult: (ValidationError?) -> Unit) { viewModelScope.launch { onResult(repo.saveCourse(d)) } }
    fun saveEvent(d: StudyEventDraft, onResult: (ValidationError?) -> Unit) { viewModelScope.launch { onResult(repo.saveEvent(d)) } }
    fun saveExam(d: ExamDraft, onResult: (ValidationError?) -> Unit) { viewModelScope.launch { onResult(repo.saveExam(d)) } }
    fun saveAssignment(d: AssignmentDraft, onResult: (ValidationError?) -> Unit) { viewModelScope.launch { onResult(repo.saveAssignment(d)) } }
    fun saveSession(d: SessionDraft, onResult: (ValidationError?) -> Unit) { viewModelScope.launch { onResult(repo.saveSession(d)) } }

    fun deleteCourse(id: Long) { viewModelScope.launch { repo.deleteCourse(id) } }
    fun deleteEvent(id: Long) { viewModelScope.launch { repo.deleteEvent(id) } }
    fun deleteExam(id: Long) { viewModelScope.launch { repo.deleteExam(id) } }
    fun deleteAssignment(id: Long) { viewModelScope.launch { repo.deleteAssignment(id) } }
    fun deleteSession(id: Long) { viewModelScope.launch { repo.deleteSession(id) } }
    fun setAssignmentCompleted(id: Long, done: Boolean) { viewModelScope.launch { repo.setAssignmentCompleted(id, done) } }
    fun setSessionCompleted(id: Long, done: Boolean) { viewModelScope.launch { repo.setSessionCompleted(id, done) } }

    companion object {
        fun factory(repo: StudyRepository) = viewModelFactory { initializer { StudyViewModel(repo) } }
    }
}
