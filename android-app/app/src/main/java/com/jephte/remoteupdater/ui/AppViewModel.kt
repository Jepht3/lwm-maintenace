package com.jephte.remoteupdater.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jephte.remoteupdater.data.ActivityLog
import com.jephte.remoteupdater.data.ApiException
import com.jephte.remoteupdater.data.LogEntry
import com.jephte.remoteupdater.data.Site
import com.jephte.remoteupdater.data.SiteData
import com.jephte.remoteupdater.data.SiteRepository
import com.jephte.remoteupdater.data.WpApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SiteState(
    val site: Site,
    val data: SiteData? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** Dernière lecture réussie (ms). */
    val fetchedAt: Long = 0,
    /** Opérations hors file d'attente : "upload:<type>", "auto:<id>". */
    val busy: Set<String> = emptySet(),
) {
    /** Éléments à mettre à jour (les traductions comptent pour un). */
    val pending: List<UpdateJob> get() = pendingJobs(this)
    val updateCount: Int get() = pending.size
}

enum class JobKind(val order: Int, val label: String) {
    PLUGIN(0, "Extension"), THEME(1, "Thème"), TRANSLATIONS(2, "Traductions"), CORE(3, "WordPress")
}

enum class JobState { QUEUED, RUNNING, DONE, FAILED }

/** Une mise à jour unitaire, suivie de bout en bout dans l'interface. */
data class UpdateJob(
    val siteId: String,
    val siteName: String,
    val kind: JobKind,
    /** Fichier de l'extension, slug du thème, "core" ou "translations". */
    val key: String,
    val name: String,
    val from: String,
    val to: String,
    val state: JobState = JobState.QUEUED,
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    val message: String? = null,
) {
    val id: String get() = "$siteId|${kind.name}|$key"
}

/** Mises à jour disponibles d'un site, dans l'ordre d'exécution conseillé. */
fun pendingJobs(s: SiteState): List<UpdateJob> {
    val d = s.data ?: return emptyList()
    val n = s.site.name
    val out = mutableListOf<UpdateJob>()
    d.plugins.filter { it.updateAvailable }.forEach {
        out += UpdateJob(s.site.id, n, JobKind.PLUGIN, it.file, it.name, it.version, it.newVersion.orEmpty())
    }
    d.themes.filter { it.updateAvailable }.forEach {
        out += UpdateJob(s.site.id, n, JobKind.THEME, it.slug, it.name, it.version, it.newVersion.orEmpty())
    }
    if (d.translations.isNotEmpty()) {
        out += UpdateJob(
            s.site.id, n, JobKind.TRANSLATIONS, "translations", "Traductions",
            "${d.translations.size} paquet(s)", d.translations.map { it.language }.distinct().joinToString(),
        )
    }
    if (d.core.updateAvailable) {
        out += UpdateJob(s.site.id, n, JobKind.CORE, "core", "WordPress", d.core.current, d.core.newVersion.orEmpty())
    }
    return out
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SiteRepository(app)
    private val activityLog = ActivityLog(app)

    private val _sites = MutableStateFlow(repo.load().map { SiteState(it) })
    val sites: StateFlow<List<SiteState>> = _sites

    private val _history = MutableStateFlow(activityLog.load())
    val history: StateFlow<List<LogEntry>> = _history

    /** File d'attente des mises à jour, indexée par [UpdateJob.id]. */
    private val _jobs = MutableStateFlow<Map<String, UpdateJob>>(emptyMap())
    val jobs: StateFlow<Map<String, UpdateJob>> = _jobs

    private val workers = mutableMapOf<String, Job>()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        refreshAll()
    }

    private fun persist() = repo.save(_sites.value.map { it.site })

    private fun mutate(id: String, block: (SiteState) -> SiteState) =
        _sites.update { list -> list.map { if (it.site.id == id) block(it) else it } }

    private fun state(id: String) = _sites.value.firstOrNull { it.site.id == id }

    fun notify(msg: String) {
        _messages.trySend(msg)
    }

    private fun log(site: String, text: String, ok: Boolean) {
        _history.update { (listOf(LogEntry(System.currentTimeMillis(), site, text, ok)) + it).take(ActivityLog.MAX) }
        activityLog.save(_history.value)
    }

    fun clearHistory() {
        _history.value = emptyList()
        activityLog.save(emptyList())
    }

    /* ---- Sites ---- */

    suspend fun addSite(site: Site): String? {
        val existing = _sites.value.firstOrNull { it.site.restBase == site.restBase }
        return try {
            val info = WpApi(site).status()
            if (existing != null) {
                mutate(existing.site.id) { it.copy(site = site.copy(id = existing.site.id, name = existing.site.name)) }
                persist()
                refresh(existing.site.id)
            } else {
                val named = site.copy(name = info.name.ifEmpty { site.name })
                _sites.update { it + SiteState(named) }
                persist()
                refresh(named.id)
                log(named.name, "Site connecté", true)
            }
            null
        } catch (e: ApiException) {
            e.message
        }
    }

    fun removeSite(id: String) {
        val name = state(id)?.site?.name
        cancelQueue(id)
        _sites.update { list -> list.filterNot { it.site.id == id } }
        persist()
        if (name != null) log(name, "Site retiré de l'application", true)
    }

    fun renameSite(id: String, name: String) {
        if (name.isBlank()) return
        mutate(id) { it.copy(site = it.site.copy(name = name.trim())) }
        persist()
    }

    /** Efface tous les sites (suppression du compte). */
    fun wipeAll() {
        workers.values.forEach { it.cancel() }
        workers.clear()
        _jobs.value = emptyMap()
        _sites.value = emptyList()
        persist()
        clearHistory()
    }

    /* ---- Lecture ---- */

    fun refreshAll(force: Boolean = false) = _sites.value.forEach { refresh(it.site.id, force) }

    fun refresh(id: String, force: Boolean = false) {
        val s = state(id) ?: return
        if (s.loading) return
        mutate(id) { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val data = WpApi(s.site).updates(refresh = force)
                mutate(id) { it.copy(data = data, loading = false, error = null, fetchedAt = System.currentTimeMillis()) }
            } catch (e: ApiException) {
                mutate(id) { it.copy(loading = false, error = e.message) }
            }
        }
    }

    /* ---- File d'attente des mises à jour ---- */

    private fun setJob(job: UpdateJob) = _jobs.update { it + (job.id to job) }

    /** Tâches du site encore actives (en file ou en cours). */
    fun activeJobs(siteId: String) = _jobs.value.values.filter { it.siteId == siteId && (it.state == JobState.QUEUED || it.state == JobState.RUNNING) }

    /** Ajoute des mises à jour à la file et démarre le traitement du site. */
    fun enqueue(items: List<UpdateJob>) {
        if (items.isEmpty()) return
        items.groupBy { it.siteId }.forEach { (siteId, list) ->
            // Nouvelle série sur un site au repos : on efface les résultats de la précédente.
            if (activeJobs(siteId).isEmpty()) _jobs.update { m -> m.filterValues { it.siteId != siteId } }
            list.sortedBy { it.kind.order }.forEach { j ->
                val cur = _jobs.value[j.id]
                if (cur == null || cur.state == JobState.DONE || cur.state == JobState.FAILED) {
                    setJob(j.copy(state = JobState.QUEUED, message = null, startedAt = 0, finishedAt = 0))
                }
            }
            startWorker(siteId)
        }
    }

    fun updateEverything(siteId: String) = state(siteId)?.let { enqueue(it.pending) }

    fun updateEverywhere() = enqueue(_sites.value.flatMap { it.pending })

    fun updateOne(job: UpdateJob) = enqueue(listOf(job))

    fun retry(job: UpdateJob) = enqueue(listOf(job))

    /** Retire de la file ce qui n'a pas encore commencé (la tâche en cours se termine). */
    fun cancelQueue(siteId: String) {
        _jobs.update { m -> m.filterValues { !(it.siteId == siteId && it.state == JobState.QUEUED) } }
    }

    private fun startWorker(siteId: String) {
        if (workers[siteId]?.isActive == true) return
        workers[siteId] = viewModelScope.launch {
            var changed = false
            while (true) {
                val next = _jobs.value.values
                    .filter { it.siteId == siteId && it.state == JobState.QUEUED }
                    .minByOrNull { it.kind.order } ?: break
                val s = state(siteId) ?: break
                val running = next.copy(state = JobState.RUNNING, startedAt = System.currentTimeMillis())
                setJob(running)
                val done = runJob(WpApi(s.site), running)
                setJob(done)
                changed = true
                log(
                    s.site.name,
                    when {
                        done.state == JobState.DONE && done.kind == JobKind.TRANSLATIONS -> "Traductions mises à jour"
                        done.state == JobState.DONE -> "${done.name} : ${done.from} → ${done.to}"
                        else -> "${done.name} — échec : ${done.message}"
                    },
                    done.state == JobState.DONE,
                )
            }
            if (changed) {
                val all = _jobs.value.values.filter { it.siteId == siteId }
                val failed = all.count { it.state == JobState.FAILED }
                val ok = all.count { it.state == JobState.DONE }
                state(siteId)?.let { s ->
                    notify(
                        if (failed == 0) "${s.site.name} : $ok mise(s) à jour terminée(s)"
                        else "${s.site.name} : $ok réussie(s), $failed échec(s)",
                    )
                    com.jephte.remoteupdater.data.Notifier.batchFinished(getApplication(), s.site.name, ok, failed, siteId)
                }
                refresh(siteId)
            }
        }
    }

    private suspend fun runJob(api: WpApi, job: UpdateJob): UpdateJob {
        return try {
            when (job.kind) {
                JobKind.PLUGIN -> {
                    val r = api.updatePlugins(listOf(job.key)).firstOrNull()
                    if (r == null) job.copy(state = JobState.DONE, finishedAt = now(), message = "Déjà à jour")
                    else if (r.success) job.copy(state = JobState.DONE, finishedAt = now(), to = r.version ?: job.to)
                    else job.copy(state = JobState.FAILED, finishedAt = now(), message = r.error ?: "Échec de la mise à jour")
                }
                JobKind.THEME -> {
                    val r = api.updateThemes(listOf(job.key)).firstOrNull()
                    if (r == null) job.copy(state = JobState.DONE, finishedAt = now(), message = "Déjà à jour")
                    else if (r.success) job.copy(state = JobState.DONE, finishedAt = now(), to = r.version ?: job.to)
                    else job.copy(state = JobState.FAILED, finishedAt = now(), message = r.error ?: "Échec de la mise à jour")
                }
                JobKind.CORE -> {
                    api.updateCore()
                    job.copy(state = JobState.DONE, finishedAt = now())
                }
                JobKind.TRANSLATIONS -> {
                    api.updateTranslations()
                    job.copy(state = JobState.DONE, finishedAt = now())
                }
            }
        } catch (e: ApiException) {
            job.copy(state = JobState.FAILED, finishedAt = now(), message = e.message)
        }
    }

    private fun now() = System.currentTimeMillis()

    /* ---- Actions directes ---- */

    private fun runBusy(id: String, busyKey: String, task: suspend (WpApi) -> String?) {
        val s = state(id) ?: return
        if (busyKey in s.busy) return
        mutate(id) { it.copy(busy = it.busy + busyKey) }
        viewModelScope.launch {
            try {
                task(WpApi(s.site))?.let {
                    notify(it)
                    log(s.site.name, it, true)
                }
            } catch (e: ApiException) {
                notify("${s.site.name} : ${e.message}")
                log(s.site.name, e.message ?: "Erreur", false)
            } finally {
                mutate(id) { it.copy(busy = it.busy - busyKey) }
                refresh(id)
            }
        }
    }

    /** Mise à jour manuelle : téléverse un .zip (extension ou thème). */
    fun uploadPackage(id: String, type: String, fileName: String, bytes: ByteArray) =
        runBusy(id, "upload:$type") { api ->
            val r = api.uploadPackage(type, fileName, bytes)
            when {
                r.oldVersion != null && r.version != null && r.oldVersion != r.version -> "${r.name} : ${r.oldVersion} → ${r.version} (fichier .zip)"
                r.version != null -> "${r.name} ${r.version} — ${r.message}"
                else -> r.message
            }
        }

    fun setAutoUpdate(id: String, type: String, item: String, enabled: Boolean) {
        mutate(id) { s ->
            val d = s.data ?: return@mutate s
            s.copy(
                data = if (type == "plugin") d.copy(plugins = d.plugins.map { if (it.file == item) it.copy(autoUpdate = enabled) else it })
                else d.copy(themes = d.themes.map { if (it.slug == item) it.copy(autoUpdate = enabled) else it }),
            )
        }
        val s = state(id) ?: return
        viewModelScope.launch {
            try {
                WpApi(s.site).setAutoUpdate(type, item, enabled)
            } catch (e: ApiException) {
                notify("${s.site.name} : ${e.message}")
                refresh(id)
            }
        }
    }
}
