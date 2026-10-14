package com.m57.hermescontrol.notification.push

import android.app.Activity
import android.content.Context
import android.util.Log
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.model.PushDeviceRequest
import com.m57.hermescontrol.data.model.PushRegisterRequest
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.notification.NewMessageCheckJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.unifiedpush.android.connector.INSTANCE_DEFAULT
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.ResolvedDistributor

/** What the Settings status line shows. */
sealed interface PushStatus {
    /** Registered with the server and receiving through [distributorLabel]. */
    data class Active(
        val distributorLabel: String,
    ) : PushStatus

    /** No distributor app is installed; the user needs ntfy (or another UnifiedPush app). */
    data object NeedsDistributor : PushStatus

    /** A distributor may exist, but no endpoint is registered with the server yet. */
    data object NotRegistered : PushStatus
}

/** Outcome of the Settings "Send test" button. */
data class PushTestResult(
    val ok: Boolean,
    val httpStatus: Int? = null,
    val message: String? = null,
)

/**
 * Owns the push lifecycle: picks the distributor, keeps the server told about the current
 * endpoint and settings, and retries on the next app start when a registration did not land.
 * There is no polling and no socket: everything here runs on an event or at app start.
 */
object PushRegistrar {
    private const val TAG = "PushRegistrar"
    private const val AUTH_WAIT_MS = 15_000L
    private const val PREFS_DEBOUNCE_MS = 1_500L
    private const val MESSAGE_FOR_DISTRIBUTOR = "Hermes"

    /** Play Store listing of the ntfy distributor, shown when no distributor is installed. */
    const val NTFY_PACKAGE: String = "io.heckel.ntfy"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registerMutex = Mutex()
    private val _status = MutableStateFlow<PushStatus>(PushStatus.NotRegistered)
    private var debounceJob: Job? = null

    @Volatile
    private var startedWatching = false

    val status: StateFlow<PushStatus> = _status.asStateFlow()

    /** Recomputes [status] from the connector and the stored registration. */
    fun refreshStatus(context: Context) {
        _status.value = computeStatus(context.applicationContext)
    }

    private fun computeStatus(context: Context): PushStatus {
        val distributor = runCatching { UnifiedPush.getAckDistributor(context) }.getOrNull()
        if (distributor == null) {
            val installed = runCatching { UnifiedPush.getDistributors(context) }.getOrDefault(emptyList())
            return if (installed.none { it != context.packageName }) {
                PushStatus.NeedsDistributor
            } else {
                PushStatus.NotRegistered
            }
        }
        val registered =
            PushStore.endpoint(context) != null &&
                PushStore.lastRegisterOkMs(context) > 0L &&
                !PushStore.isRegisterPending(context)
        return if (registered) PushStatus.Active(distributorLabel(context, distributor)) else PushStatus.NotRegistered
    }

    private fun distributorLabel(
        context: Context,
        packageName: String,
    ): String =
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)

    /**
     * App start, once AuthManager is ready: adopt the user's default distributor when there is
     * one, ask it for an endpoint when we have none, and re-send a registration that failed or
     * is getting old. Also re-registers when the server or sign-in changes.
     */
    fun startup(context: Context) {
        val app = context.applicationContext
        scope.launch {
            if (!awaitAuthReady()) return@launch
            runCatching {
                var distributor = UnifiedPush.getAckDistributor(app)
                if (distributor == null) {
                    val resolved = UnifiedPush.resolveDefaultDistributor(app)
                    if (resolved is ResolvedDistributor.Found) {
                        UnifiedPush.saveDistributor(app, resolved.packageName)
                        distributor = resolved.packageName
                    }
                }
                if (distributor != null) {
                    val now = System.currentTimeMillis()
                    if (PushStore.endpoint(app) == null) {
                        requestEndpoint(app)
                    } else if (PushStore.isRegisterPending(app) ||
                        PushSafetyNet.needsRegisterRefresh(PushStore.lastRegisterOkMs(app), now)
                    ) {
                        registerNow(app)
                    }
                }
            }.onFailure { Log.w(TAG, "startup failed", it) }
            refreshStatus(app)
            NewMessageCheckJob.sync(app)
            watchConnection(app)
        }
    }

    private fun watchConnection(app: Context) {
        if (startedWatching) return
        startedWatching = true
        scope.launch {
            AuthManager.contextFlow
                .map { ctx -> ctx?.let { it.baseUrl to !it.token.isNullOrBlank() } }
                .distinctUntilChanged()
                .drop(1)
                .collect { if (PushStore.endpoint(app) != null) scheduleRegister(app) }
        }
    }

    /**
     * Settings "Set up" button: use the current or default distributor (the OS shows a picker
     * when several are installed) and ask it for an endpoint.
     */
    fun activate(
        activity: Activity,
        onDone: (Boolean) -> Unit = {},
    ) {
        val app = activity.applicationContext
        UnifiedPush.tryUseCurrentOrDefaultDistributor(activity) { success ->
            if (success) requestEndpoint(app)
            refreshStatus(app)
            onDone(success)
        }
    }

    private fun requestEndpoint(context: Context) {
        runCatching {
            UnifiedPush.register(context, INSTANCE_DEFAULT, MESSAGE_FOR_DISTRIBUTOR)
        }.onFailure { Log.w(TAG, "register with distributor failed", it) }
    }

    /** The distributor delivered a (new or changed) endpoint: remember it and tell the server. */
    fun onNewEndpoint(
        context: Context,
        endpoint: PushEndpoint,
    ) {
        val app = context.applicationContext
        val keys = endpoint.pubKeySet
        if (keys == null) {
            // Without Web Push keys the server cannot encrypt for us; nothing useful to register.
            Log.w(TAG, "endpoint without Web Push keys")
            PushStore.clearEndpoint(app)
            refreshStatus(app)
            return
        }
        PushStore.saveEndpoint(app, PushEndpointRecord(endpoint.url, keys.pubKey, keys.auth))
        refreshStatus(app)
        scope.launch {
            registerNow(app)
        }
    }

    /** The distributor dropped our registration: forget it and tell the server (best effort). */
    fun onUnregistered(context: Context) {
        val app = context.applicationContext
        val deviceId = PushStore.deviceId(app)
        PushStore.clearEndpoint(app)
        refreshStatus(app)
        NewMessageCheckJob.sync(app)
        scope.launch {
            if (awaitAuthReady()) {
                safeApiCall(retries = 0) { ApiClient.hermesApi.pushUnregister(PushDeviceRequest(deviceId)) }
            }
        }
    }

    fun onTempUnavailable(context: Context) {
        PushStore.setDegraded(context.applicationContext, true)
        NewMessageCheckJob.sync(context.applicationContext)
    }

    fun onMessageReceived(context: Context) {
        val app = context.applicationContext
        if (PushStore.isDegraded(app)) {
            PushStore.setDegraded(app, false)
            NewMessageCheckJob.sync(app)
        }
    }

    /** Settings changed: re-POST /register shortly, coalescing a burst of taps into one request. */
    fun scheduleRegister(context: Context) {
        val app = context.applicationContext
        debounceJob?.cancel()
        debounceJob =
            scope.launch {
                delay(PREFS_DEBOUNCE_MS)
                if (PushStore.endpoint(app) != null) registerNow(app)
            }
    }

    /** POSTs the stored endpoint and settings. Returns true when the server accepted them. */
    suspend fun registerNow(context: Context): Boolean =
        registerMutex.withLock {
            val app = context.applicationContext
            val endpoint = PushStore.endpoint(app) ?: return@withLock false
            if (!awaitAuthReady()) {
                PushStore.markRegisterPending(app)
                return@withLock false
            }
            val body =
                PushRegisterRequest(
                    device_id = PushStore.deviceId(app),
                    endpoint = endpoint.url,
                    p256dh = endpoint.p256dh,
                    auth = endpoint.auth,
                    prefs = PushStore.prefsValue(app).toRegisterJson(),
                )
            val result = safeApiCall(retries = 1) { ApiClient.hermesApi.pushRegister(body) }
            val ok = (result as? NetworkResult.Success)?.data?.ok == true
            if (ok) PushStore.markRegistered(app) else PushStore.markRegisterPending(app)
            refreshStatus(app)
            NewMessageCheckJob.sync(app)
            ok
        }

    /** Asks the server to push a test notification to this device. */
    suspend fun sendTest(context: Context): PushTestResult {
        val app = context.applicationContext
        if (PushStore.endpoint(app) == null) return PushTestResult(ok = false, message = "not registered")
        if (!awaitAuthReady()) return PushTestResult(ok = false, message = "not signed in")
        // Make sure the server has the latest endpoint and settings before it pushes.
        if (PushStore.isRegisterPending(app) && !registerNow(app)) {
            return PushTestResult(ok = false, message = "register failed")
        }
        val request = PushDeviceRequest(PushStore.deviceId(app))
        return when (val result = safeApiCall(retries = 0) { ApiClient.hermesApi.pushTest(request) }) {
            is NetworkResult.Success -> {
                val status = result.data?.status
                PushTestResult(
                    ok = result.data?.ok == true && (status == null || status in 200..299),
                    httpStatus = status,
                )
            }

            is NetworkResult.Failure -> {
                PushTestResult(ok = false, message = result.error.message)
            }
        }
    }

    private suspend fun awaitAuthReady(): Boolean {
        val ready =
            withTimeoutOrNull(AUTH_WAIT_MS) {
                AuthManager.initializationState.first { it == AuthManager.InitializationState.Ready }
            }
        if (ready == null) return false
        return AuthManager.isGatedMode() || !AuthManager.getToken().isNullOrBlank()
    }
}
