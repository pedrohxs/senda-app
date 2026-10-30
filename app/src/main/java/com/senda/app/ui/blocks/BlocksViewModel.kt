package com.senda.app.ui.blocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.senda.app.core.UrlMatcher
import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.GroupItemEntity
import com.senda.app.data.local.entity.TargetType
import com.senda.app.data.repository.BlocksRepository
import com.senda.app.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlocksUiState(
    val isLoading: Boolean = true,
    val groups: List<AppGroupEntity> = emptyList(),
    val selectedGroupId: String? = null,
    val selectedType: TargetType = TargetType.WEBSITE,
    val items: List<GroupItemEntity> = emptyList(),
    val errorMessage: String? = null,
    val loadFailed: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class BlocksViewModel(
    private val repository: BlocksRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlocksUiState())
    val uiState: StateFlow<BlocksUiState> = _uiState.asStateFlow()

    private val selectedGroupId = MutableStateFlow<String?>(null)
    private val selectedType = MutableStateFlow(TargetType.WEBSITE)
    private var groupsJob: Job? = null
    private var itemsJob: Job? = null

    init {
        observeGroups()
        observeItems()
    }

    /** Nova tentativa depois de uma falha de leitura do banco. */
    fun retry() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        observeGroups()
        observeItems()
    }

    fun selectGroup(groupId: String) {
        selectedGroupId.value = groupId
        _uiState.update {
            it.copy(selectedGroupId = groupId, errorMessage = null)
        }
    }

    fun selectType(type: TargetType) {
        selectedType.value = type
        _uiState.update {
            it.copy(selectedType = type, errorMessage = null)
        }
    }

    fun addWebsite(host: String) {
        val groupId = _uiState.value.selectedGroupId ?: return
        val normalizedHost = UrlMatcher.extractHost(host)
        if (normalizedHost == null) {
            _uiState.update { it.copy(errorMessage = "Informe um domínio válido.") }
            return
        }

        addItem(
            GroupItemEntity(
                groupId = groupId,
                targetType = TargetType.WEBSITE,
                targetIdentifier = normalizedHost,
                displayName = normalizedHost
            )
        )
    }

    fun addKeyword(keyword: String) {
        val groupId = _uiState.value.selectedGroupId ?: return
        val value = keyword.trim()
        if (value.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Informe uma palavra-chave.") }
            return
        }

        addItem(
            GroupItemEntity(
                groupId = groupId,
                targetType = TargetType.KEYWORD,
                targetIdentifier = value,
                displayName = value
            )
        )
    }

    fun deleteItem(item: GroupItemEntity) {
        viewModelScope.launch {
            runCatching {
                repository.deleteItem(item)
            }.onFailure {
                _uiState.update {
                    it.copy(errorMessage = "Não foi possível remover o bloqueio.")
                }
            }
        }
    }

    private fun observeGroups() {
        groupsJob?.cancel()
        groupsJob = viewModelScope.launch {
            repository.observeGroups()
                .catch {
                    _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                }
                .collect { groups ->
                    val currentSelection = selectedGroupId.value
                    val validSelection = currentSelection?.takeIf { id ->
                        groups.any { group -> group.id == id }
                    }
                    val selected = validSelection ?: groups.firstOrNull()?.id

                    if (selectedGroupId.value != selected) {
                        selectedGroupId.value = selected
                    }

                    _uiState.update {
                        it.copy(
                            groups = groups,
                            selectedGroupId = selected,
                            isLoading = false,
                            loadFailed = false
                        )
                    }
                }
        }
    }

    private fun observeItems() {
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            combine(selectedGroupId, selectedType) { groupId, type ->
                groupId to type
            }.flatMapLatest { (groupId, type) ->
                if (groupId == null) {
                    flowOf(emptyList())
                } else {
                    repository.observeItems(groupId).map { items ->
                        items.filter { it.targetType == type }
                    }
                }
            }.catch {
                _uiState.update { it.copy(loadFailed = true) }
            }.collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
    }

    private fun addItem(item: GroupItemEntity) {
        viewModelScope.launch {
            runCatching {
                repository.addItem(item)
            }.onSuccess {
                _uiState.update { it.copy(errorMessage = null) }
            }.onFailure {
                _uiState.update {
                    it.copy(errorMessage = "Não foi possível adicionar o bloqueio.")
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BlocksViewModel(repository = container.blocksRepository)
            }
        }
    }
}
