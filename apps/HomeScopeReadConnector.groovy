import groovy.json.JsonBuilder
import groovy.transform.Field

definition(
    name: "HomeScope Read Connector",
    namespace: "homescope",
    author: "HomeScope",
    description: "Owner-scoped, read-only Hubitat discovery for HomeScope's local collector.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    singleInstance: false,
    singleThreaded: true,
    oauth: true
)

preferences {
    page(name: "mainPage")
}

mappings {
    path("/v1/metadata") { action: [GET: "metadata"] }
    path("/v1/capabilities") { action: [GET: "capabilities"] }
    path("/v1/inventory") { action: [GET: "inventory"] }
    path("/v1/state") { action: [GET: "state"] }
    path("/v1/events") { action: [GET: "events"] }
    path("/v1/topology") { action: [GET: "topology"] }
    path("/v1/exposure") { action: [GET: "exposure"] }
}

@Field static final String CONNECTOR_ID = "homescope.hubitat.read"
@Field static final String CONNECTOR_VERSION = "1.0.0"
@Field static final String CONTRACT_VERSION = "1.0.0"
@Field static final int MAX_PAGE_RECORDS = 250
@Field static final int MAX_PAGE_BYTES = 262144
@Field static final int PAGE_CONTENT_BUDGET = 200000
@Field static final int MAX_SNAPSHOT_PAGES = 20
@Field static final int MAX_SNAPSHOT_RECORDS = 5000
@Field static final int MAX_SELECTED_DEVICES = 250
@Field static final int MAX_CURSOR_REGISTRY = 8
@Field static final long CURSOR_TTL_MS = 300000L
@Field static final int MAX_TEXT_LENGTH = 256
@Field static final int MAX_CAPABILITIES_PER_DEVICE = 128
@Field static final int MAX_ATTRIBUTES_PER_DEVICE = 128
@Field static final int MAX_COMMANDS_PER_DEVICE = 128
@Field static final int MAX_TOPOLOGY_DEVICES = 48
@Field static final int MAX_ROOM_DETAIL_LIMITATIONS = 24
@Field static final int MAX_DEVICE_CHILD_REFS = 249
@Field static final int MAX_SAFE_NETWORK_ID_LENGTH = 4
@Field static final int MAX_EVENT_HISTORY = 50
@Field static final int MAX_EVENT_DELIVERY_BYTES = 32768
@Field static final int MAX_EVENT_DELIVERY_ATTEMPTS = 3
@Field static final int MAX_EVENT_SUBSCRIPTION_DEVICES = 16
@Field static final int MAX_EVENT_SUBSCRIPTION_ATTRIBUTES = 8
@Field static final int EVENT_DELIVERY_BURST = 10

def mainPage() {
    dynamicPage(name: "mainPage", title: "HomeScope Read Connector", install: true, uninstall: true) {
        section("Owner-selected discovery scope") {
            input(
                name: "scopeProfile",
                type: "enum",
                title: "HomeScope read profile",
                description: "Safe empty is the fail-closed default. Profile changes require this owner page.",
                options: [
                    safe_empty: "Safe empty (no devices or evidence)",
                    minimal_polling: "Minimal polling (selected-device inventory and state)",
                    custom: "Custom owner-selected evidence"
                ],
                defaultValue: "safe_empty",
                required: true,
                submitOnChange: true
            )
            String profile = activeScopeProfile()
            if (profile == "minimal_polling" || profile == "custom") {
                input(
                    name: "profileSelectedDevices",
                    type: "capability.*",
                    title: "Devices HomeScope may inspect",
                    description: "Select only the minimum devices needed.",
                    multiple: true,
                    required: false
                )
            }
            if (profile == "custom") {
                paragraph "Evidence categories are individually disabled by default. Enable only what is needed."
                evidenceCategoryOptions().each { String category, String label ->
                    String settingName = "customEvidence_${category}".toString()
                    input(
                        name: settingName,
                        type: "bool",
                        title: label,
                        defaultValue: false,
                        required: false,
                        submitOnChange: true
                    )
                }
                input(
                    name: "applyCustomEvidenceSelection",
                    type: "button",
                    title: "Apply custom evidence selection"
                )
                paragraph "New custom evidence categories have no authority until you apply them here. " +
                    "Turning a category off removes its authority immediately."
            }
        }
        section("LAN-local OAuth credential") {
            paragraph "Enable OAuth for this app code. HomeScope's collector and live validation checkpoint " +
                "must verify that the configured app-instance endpoint is private-LAN. This app cannot make " +
                "Hubitat cloud endpoints unavailable."
            if (state.accessToken) {
                boolean revealCredential = state.revealCredentialOnce == true
                state.remove("revealCredentialOnce")
                if (revealCredential) {
                    paragraph "Local API base: ${getFullLocalApiServerUrl()}"
                    paragraph "Access token (copy only into the approved local secret store): ${state.accessToken}"
                } else {
                    paragraph "The local API base and access token are hidden. Reveal only when copying them locally."
                    input(
                        name: "revealAccessCredential",
                        type: "button",
                        title: "Reveal local API base and access token once"
                    )
                }
                input(
                    name: "rotateAccessToken",
                    type: "button",
                    title: "Rotate this app instance's access token"
                )
            } else {
                paragraph "The dedicated token is created when this app instance is installed."
            }
        }
        if (activeScopeProfile() == "custom") {
            section("Optional adjacent-LAN event delivery") {
                paragraph "Event retention and delivery are disabled by default. Select only the few device attributes " +
                    "HomeScope needs; broad all-event subscriptions are intentionally unavailable."
                input(
                    name: "customEventSubscriptionsEnabled",
                    type: "bool",
                    title: "Retain explicitly selected device attributes",
                    defaultValue: false,
                    required: false,
                    submitOnChange: true
                )
                if (settings?.customEventSubscriptionsEnabled == true) {
                    input(
                        name: "customEventDevices",
                        type: "capability.*",
                        title: "Event devices (maximum 16; must also be discovery-selected)",
                        multiple: true,
                        required: false
                    )
                    input(
                        name: "customEventAttributes",
                        type: "enum",
                        title: "Event attributes (maximum 8)",
                        options: eventAttributeOptions(),
                        multiple: true,
                        required: false
                    )
                    input(
                        name: "customEventDeliveryEnabled",
                        type: "bool",
                        title: "Deliver owner-selected device events",
                        description: "Disabled by default. Polling remains the recovery path.",
                        defaultValue: false,
                        required: false,
                        submitOnChange: true
                    )
                    if (settings?.customEventDeliveryEnabled == true) {
                        input(
                            name: "customEventCallbackUrl",
                            type: "text",
                            title: "Fixed HomeScope callback URL",
                            description: "Exact HTTPS private-LAN URL ending /v1/hubitat/events. It is configured only here.",
                            required: false
                        )
                    }
                }
                paragraph "Delivery is opportunistic, one event at a time, and never accepts instructions in a response."
            }
        }
        section("Safety boundary") {
            paragraph "This app exposes seven fixed inbound GET endpoints only. Optional owner-selected event " +
                "delivery uses one fixed outbound callback and never invokes a device operation."
        }
    }
}

def installed() {
    state.remove("revealCredentialOnce")
    state.remove("approvedCustomEvidence")
    state.remove("authorityScopeProfile")
    clearLegacyAuthoritySettings()
    activeScopeProfile()
    ensureAccessToken()
    configureEventDelivery()
}

def updated() {
    state.remove("revealCredentialOnce")
    clearLegacyAuthoritySettings()
    activeScopeProfile()
    ensureAccessToken()
    configureEventDelivery()
}

def uninstalled() {
    unsubscribe()
    unschedule()
    clearEventDeliveryState()
    clearEventHistory()
    state.remove("cursorRegistry")
    state.remove("approvedCustomEvidence")
    state.remove("authorityScopeProfile")
    if (state.accessToken) {
        revokeAccessToken()
        state.remove("accessToken")
    }
}

def appButtonHandler(String buttonName) {
    state.remove("revealCredentialOnce")
    if (buttonName == "revealAccessCredential") {
        state.revealCredentialOnce = true
        return
    }
    if (buttonName == "applyCustomEvidenceSelection") {
        if (activeScopeProfile() == "custom") {
            state.approvedCustomEvidence = candidateCustomEvidenceCategories()
            state.authorityScopeProfile = "custom"
        } else {
            state.remove("approvedCustomEvidence")
        }
        return
    }
    if (buttonName != "rotateAccessToken") {
        return
    }
    if (state.accessToken) {
        revokeAccessToken()
        state.remove("accessToken")
    }
    state.remove("cursorRegistry")
    clearEventDeliveryState()
    ensureAccessToken()
}

private void configureEventDelivery() {
    unsubscribe()
    unschedule("retryPendingEventDelivery")
    clearEventDeliveryState()
    clearEventHistory()
    Map selection = selectedEventSubscriptionSelection()
    if (!selection.enabled || (selection.devices as List).isEmpty() || (selection.attributes as List).isEmpty()) {
        return
    }
    state.eventHistoryStartedAt = isoTime()
    (selection.devices as List).each { device ->
        (selection.attributes as List).each { String attribute ->
            subscribe(device, attribute, "selectedDeviceEventHandler")
        }
    }
}

private void clearEventDeliveryState() {
    state.remove("eventDeliveryInFlight")
    state.remove("pendingEventDelivery")
    state.remove("pendingEventAttempt")
}

private void clearEventHistory() {
    state.remove("eventHistory")
    state.remove("eventHistoryTruncated")
    state.remove("eventHistoryStartedAt")
    state.remove("eventHistoryDroppedCount")
    state.remove("eventRecordDroppedCount")
    state.remove("eventDeliveryDroppedCount")
    state.remove("eventDeliveryCoalescedCount")
    state.remove("eventDeliveryRateState")
}

private void clearLegacyAuthoritySettings() {
    [
        "selectedDevices",
        "selectedEvidence",
        "eventSubscriptionsEnabled",
        "selectedEventDevices",
        "selectedEventAttributes",
        "eventDeliveryEnabled",
        "eventCallbackUrl"
    ].each { String settingName -> app.removeSetting(settingName) }
}

private void reconcileScopeProfileAuthority(String profile) {
    String prior = state.authorityScopeProfile instanceof CharSequence ?
        state.authorityScopeProfile.toString() : null
    if (prior != profile) {
        state.remove("approvedCustomEvidence")
        state.authorityScopeProfile = profile
    }
    if (profile != "custom") {
        state.remove("approvedCustomEvidence")
        return
    }
    List<String> approved = state.approvedCustomEvidence instanceof Collection ?
        state.approvedCustomEvidence as List<String> : []
    Set<String> candidates = candidateCustomEvidenceCategories() as Set<String>
    Set<String> allowed = evidenceCategoryOptions().keySet()
    List<String> pruned = approved.collect { String category -> category.toString() }
        .findAll { String category -> allowed.contains(category) && candidates.contains(category) }
        .unique()
        .sort()
    if (pruned.isEmpty()) {
        state.remove("approvedCustomEvidence")
    } else if (state.approvedCustomEvidence != pruned) {
        state.approvedCustomEvidence = pruned
    }
}

private boolean eventDeliveryConfigured() {
    Map selection = selectedEventSubscriptionSelection()
    return selection.enabled == true &&
        !(selection.devices as List).isEmpty() &&
        !(selection.attributes as List).isEmpty() &&
        settings?.customEventDeliveryEnabled == true &&
        safeEventCallbackUrl(settings?.customEventCallbackUrl) != null
}

private void ensureAccessToken() {
    if (!state.accessToken) {
        createAccessToken()
    }
}

def metadata() {
    Map selection = selectedDeviceSelection()
    String observedAt = isoTime()
    String coverageId = coverageIdFor("metadata")
    Map record = baseRecord(
        "hubitat.connector-metadata",
        "connector-metadata",
        app.id,
        observedAt,
        coverageId
    )
    record.connector_version = CONNECTOR_VERSION
    record.contract_version = CONTRACT_VERSION
    record.selected_categories = selectedEvidenceCategories()
    record.native_extensions = [
        route_manifest: connectorManifest(),
        selection_default: "empty",
        selected_device_count: selection.selected_count,
        admitted_device_count: selection.admitted_count,
        selection_truncated: selection.overflow,
        local_oauth: true
    ]
    return discoveryEnvelope("metadata", "metadata", "connector", selection, [record], true, [])
}

def capabilities() {
    Map selection = selectedDeviceSelection()
    String observedAt = isoTime()
    String coverageId = coverageIdFor("capabilities")
    Map record = baseRecord(
        "hubitat.connector-metadata",
        "connector-capabilities",
        app.id,
        observedAt,
        coverageId
    )
    record.connector_version = CONNECTOR_VERSION
    record.contract_version = CONTRACT_VERSION
    record.selected_categories = selectedEvidenceCategories()
    record.native_extensions = [
        available_operations: routeManifest().collect { Map route -> route.operation },
        selected_evidence: selectedEvidenceCategories(),
        selected_device_count: selection.selected_count,
        admitted_device_count: selection.admitted_count,
        selection_truncated: selection.overflow,
        bounds: connectorLimits(),
        command_metadata_invocable: false,
        unsupported_scopes: [
            "administrative-pages",
            "complete-installed-app-export",
            "complete-rule-implementation",
            "undocumented-platform-diagnostics"
        ]
    ]
    return discoveryEnvelope("capabilities", "metadata", "capabilities", selection, [record], true, [])
}

def inventory() {
    Map selection = selectedDeviceSelection()
    if (!deviceScopeSelected(selection, "inventory")) {
        return discoveryEnvelope(
            "inventory",
            "inventory",
            "selected-devices",
            selection,
            [],
            false,
            [ownerSelectionLimitation("inventory")]
        )
    }

    String observedAt = isoTime()
    String coverageId = coverageIdFor("inventory")
    Map roomContexts = readDeviceRoomContexts(
        selection.devices as List,
        "inventory",
        "selected-devices"
    )
    Map identityContexts = readSelectedDeviceIdentityContexts(
        selection.devices as List,
        "inventory",
        "selected-devices"
    )
    List<Map> records = []
    selection.devices.each { device ->
        Map roomContext = (roomContexts.by_native_id as Map)[device.id.toString()] as Map
        Map identityContext = (identityContexts.by_native_id as Map)[device.id.toString()] as Map
        records.add(deviceRecord(device, observedAt, coverageId, roomContext, identityContext))
        records.addAll(capabilityRecords(device, observedAt, coverageId))
    }
    List<Map> limitations = new ArrayList<Map>(roomContexts.limitations as List<Map>)
    limitations.addAll(identityContexts.limitations as List<Map>)
    return discoveryEnvelope(
        "inventory", "inventory", "selected-devices", selection, records, true,
        limitations
    )
}

def state() {
    Map selection = selectedDeviceSelection()
    List<String> enabled = selectedEvidenceCategories()
    boolean stateEnabled = enabled.contains("state")
    boolean modesEnabled = enabled.contains("modes")
    boolean healthEnabled = enabled.contains("health")
    boolean deviceStateSelected = selection.admitted_count > 0 && (stateEnabled || healthEnabled)
    if (!deviceStateSelected && !modesEnabled) {
        return discoveryEnvelope(
            "state",
            "state",
            "selected-current-state-and-modes",
            selection,
            [],
            false,
            [ownerSelectionLimitation("state")]
        )
    }

    String observedAt = isoTime()
    String coverageId = coverageIdFor("state")
    List<Map> records = []
    if (stateEnabled && selection.admitted_count > 0) {
        selection.devices.each { device -> records.addAll(attributeRecords(device, observedAt, coverageId)) }
    }
    if (modesEnabled) {
        records.addAll(modeRecords(observedAt, coverageId))
    }
    if (healthEnabled && selection.admitted_count > 0) {
        selection.devices.each { device -> records.add(healthRecord(device, observedAt, coverageId)) }
    }
    return discoveryEnvelope("state", "state", "selected-current-state-and-modes", selection, records, true, [])
}

def events() {
    Map selection = selectedDeviceSelection()
    if (!deviceScopeSelected(selection, "events")) {
        return discoveryEnvelope(
            "events",
            "events",
            "selected-devices",
            selection,
            [],
            false,
            [ownerSelectionLimitation("events")]
        )
    }
    Map requestWindow = boundedEventRequestWindow()
    if (!requestWindow.valid) {
        return discoveryEnvelope(
            "events", "events", "selected-devices", selection, [], false, [requestWindow.limitation], requestWindow
        )
    }
    List<Map> arrivalHistory = admittedEventHistory().findAll { Map record ->
        String sourceTime = record.source_event_at
        sourceTime != null && sourceTime >= requestWindow.from && sourceTime <= requestWindow.to
    }
    List<Map> history = arrivalHistory.toList().sort { left, right ->
        int timeOrder = left.source_event_at.toString() <=> right.source_event_at.toString()
        return timeOrder != 0 ? timeOrder : left.record_id.toString() <=> right.record_id.toString()
    }
    int requestedLimit = boundedEventLimit()
    boolean limitTruncated = history.size() > requestedLimit
    List<Map> returnedEvents = history.takeRight(requestedLimit)
    Map eventSelection = selectedEventSubscriptionSelection()
    long historyDropped = eventCounter("eventHistoryDroppedCount")
    long recordDropped = eventCounter("eventRecordDroppedCount")
    long deliveryDropped = eventCounter("eventDeliveryDroppedCount")
    long deliveryCoalesced = eventCounter("eventDeliveryCoalescedCount")
    List<Map> limitations = [[
        code: "events.bounded-retention",
        message: "Only the connector's fixed recent owner-selected subscription history is available.",
        affected_scope: "events:selected-devices",
        retryable: false,
        evidence_refs: []
    ]]
    if (state.eventHistoryTruncated == true || limitTruncated) {
        limitations.add([
            code: "events.truncated",
            message: "Older selected-device events were omitted by the fixed history or request bound.",
            affected_scope: "events:selected-devices",
            retryable: false,
            evidence_refs: []
        ])
    }
    if (eventSelection.device_overflow || eventSelection.attribute_overflow || eventSelection.scope_rejected) {
        limitations.add([
            code: "events.subscription-bound",
            message: "Event subscriptions beyond the fixed device or attribute caps were not admitted.",
            affected_scope: "events:selected-device-attributes",
            retryable: false,
            evidence_refs: []
        ])
    }
    if (!eventSelection.enabled || (eventSelection.devices as List).isEmpty() ||
        (eventSelection.attributes as List).isEmpty()) {
        limitations.add([
            code: "events.subscription-not-selected",
            message: "No bounded owner-selected device and attribute subscription is active.",
            affected_scope: "events:selected-device-attributes",
            retryable: false,
            evidence_refs: []
        ])
    }
    if (historyDropped > 0L || recordDropped > 0L) {
        limitations.add([
            code: "events.ingestion-dropped",
            message: "One or more selected event records were rejected or omitted by fixed ingestion bounds.",
            affected_scope: "events:selected-device-attributes",
            retryable: false,
            evidence_refs: []
        ])
    }
    if (deliveryDropped > 0L || deliveryCoalesced > 0L) {
        limitations.add([
            code: "event-delivery.gap",
            message: "Optional outbound delivery dropped or coalesced events; bounded GET polling remains authoritative.",
            affected_scope: "events:optional-outbound-delivery",
            retryable: true,
            evidence_refs: []
        ])
    }
    String coverageId = coverageIdFor("events")
    String observedAt = isoTime()
    String firstTime = returnedEvents.isEmpty() ? null : returnedEvents.first().source_event_at
    String lastTime = returnedEvents.isEmpty() ? null : returnedEvents.last().source_event_at
    List<Map> gaps = []
    String configuredBoundary = state.eventHistoryStartedAt instanceof CharSequence ?
        state.eventHistoryStartedAt.toString() : null
    List<Map> retainedHistory = eventHistory()
    String retentionBoundary = state.eventHistoryTruncated == true && !retainedHistory.isEmpty() ?
        retainedHistory.first().source_event_at.toString() : configuredBoundary
    String returnedFrom = retentionBoundary == null || retentionBoundary > requestWindow.to ? null :
        retentionBoundary > requestWindow.from ? retentionBoundary : requestWindow.from
    if (limitTruncated && firstTime != null) {
        returnedFrom = firstTime
    }
    boolean unknownIngestionGap = historyDropped > 0L || recordDropped > 0L
    if (unknownIngestionGap) {
        gaps.add([from: requestWindow.from, to: requestWindow.to])
    } else if (returnedFrom == null) {
        gaps.add([from: requestWindow.from, to: requestWindow.to])
    } else if (returnedFrom > requestWindow.from) {
        gaps.add([from: requestWindow.from, to: returnedFrom])
    }
    Map window = baseRecord("hubitat.event-window", "event-window.${app.id}", "event-window.${app.id}", observedAt, coverageId)
    window.requested = [from: requestWindow.from, to: requestWindow.to]
    window.returned = returnedFrom == null ? null : [from: returnedFrom, to: requestWindow.to]
    window.ordering = returnedEvents.isEmpty() ? "unknown" : "source-time"
    window.truncated = state.eventHistoryTruncated == true || limitTruncated || unknownIngestionGap ||
        deliveryDropped > 0L || deliveryCoalesced > 0L
    window.gaps = gaps
    window.retention_boundary = retentionBoundary
    List<String> returnedIds = returnedEvents.collect { Map record -> record.record_id.toString() }
    List<String> arrivalReturnedIds = arrivalHistory.findAll { Map record ->
        returnedIds.contains(record.record_id.toString())
    }.collect { Map record -> record.record_id.toString() }
    window.received_count = history.size()
    window.returned_count = returnedEvents.size()
    window.page_count = 1
    window.first_source_event_at = firstTime
    window.last_source_event_at = lastTime
    window.duplicate_count = 0
    window.reordered = returnedEvents.isEmpty() ? null : arrivalReturnedIds != returnedIds
    return discoveryEnvelope(
        "events", "events", "selected-devices", selection, [window] + returnedEvents, true, limitations, requestWindow
    )
}

def selectedDeviceEventHandler(evt) {
    if (evt == null) {
        return
    }
    Map selection = selectedEventSubscriptionSelection()
    boolean selectedDevice = (selection.devices as List).any { device ->
        device.id.toString() == evt.deviceId?.toString()
    }
    boolean selectedAttribute = (selection.attributes as List).contains(evt.name?.toString())
    if (!selection.enabled || !selectedDevice || !selectedAttribute) {
        incrementEventCounter("eventRecordDroppedCount")
        return
    }
    Map eventRecord = admittedSubscriptionEvent(evt)
    if (eventRecord == null) {
        incrementEventCounter("eventRecordDroppedCount")
        return
    }
    List<Map> history = admittedEventHistory()
    history.add(eventRecord)
    if (history.size() > MAX_EVENT_HISTORY) {
        history = history.takeRight(MAX_EVENT_HISTORY)
        state.eventHistoryTruncated = true
        incrementEventCounter("eventHistoryDroppedCount")
    }
    state.eventHistory = history
    if (eventDeliveryConfigured()) {
        sendEventDelivery(eventRecord, 1)
    }
}

private void incrementEventCounter(String name) {
    long current = eventCounter(name)
    state[name] = Math.min(1000000L, current + 1L)
}

private long eventCounter(String name) {
    return state[name] instanceof Number ? state[name] as long : 0L
}

private Map admittedSubscriptionEvent(evt) {
    Object scalar = safeEventScalar(evt.value)
    if (scalar == null && evt.value != null) {
        return null
    }
    String sourceTime = isoTimeOrNull(evt.date)
    if (sourceTime == null) {
        return null
    }
    String observedAt = isoTime()
    String eventNativeId = boundedText(evt.id)
    String subjectId = safeId(evt.deviceId, "device")
    Map record = baseRecord(
        "hubitat.event",
        "event.${eventNativeId ?: UUID.randomUUID()}",
        eventNativeId,
        observedAt,
        coverageIdFor("events")
    )
    record.subject_ref = recordId("device", subjectId)
    record.name = boundedText(evt.name) ?: "unknown"
    record.value = scalar
    record.unit = boundedText(evt.unit)
    record.source_event_at = sourceTime
    record.sequence = eventSequence(evt)
    record.event_kind = "state-event"
    record.invocable = false
    return record
}

private Long eventSequence(evt) {
    Object sequence = evt.id
    if (sequence instanceof Number && (sequence as long) >= 0L) {
        return sequence as long
    }
    return null
}

private void sendEventDelivery(Map eventRecord, int attempt) {
    if (!eventDeliveryConfigured() || attempt > MAX_EVENT_DELIVERY_ATTEMPTS) {
        return
    }
    if (state.eventDeliveryInFlight == true || state.pendingEventDelivery instanceof Map) {
        incrementEventCounter("eventDeliveryCoalescedCount")
        return
    }
    if (!admitEventDeliveryRate()) {
        incrementEventCounter("eventDeliveryDroppedCount")
        return
    }
    String sentAt = isoTime()
    Map envelope = [
        contract_version: CONTRACT_VERSION,
        connector_id: CONNECTOR_ID,
        delivery_id: safeId("delivery.${app.id}.${UUID.randomUUID()}", "delivery.event"),
        sent_at: sentAt,
        event: outboundEventRecord(eventRecord)
    ]
    deliverEventEnvelope(envelope, attempt)
}

private boolean admitEventDeliveryRate() {
    long currentTimeMs = now()
    Map rate = state.eventDeliveryRateState instanceof Map ? state.eventDeliveryRateState as Map : [:]
    long previousTimeMs = rate.last_ms instanceof Number ? rate.last_ms as long : currentTimeMs
    BigDecimal previousTokens = rate.tokens instanceof Number ? new BigDecimal(rate.tokens.toString()) :
        new BigDecimal(EVENT_DELIVERY_BURST)
    long elapsedMs = Math.max(0L, currentTimeMs - previousTimeMs)
    BigDecimal replenished = previousTokens + (new BigDecimal(elapsedMs) / new BigDecimal(1000))
    BigDecimal available = replenished.min(new BigDecimal(EVENT_DELIVERY_BURST))
    boolean admitted = available >= BigDecimal.ONE
    state.eventDeliveryRateState = [
        last_ms: currentTimeMs,
        tokens: admitted ? available - BigDecimal.ONE : available
    ]
    return admitted
}

private void deliverEventEnvelope(Map envelope, int attempt) {
    if (!eventDeliveryConfigured()) {
        incrementEventCounter("eventDeliveryDroppedCount")
        clearEventDeliveryState()
        return
    }
    String body = new JsonBuilder(envelope).toString()
    if (body.getBytes("UTF-8").length > MAX_EVENT_DELIVERY_BYTES) {
        incrementEventCounter("eventDeliveryDroppedCount")
        clearEventDeliveryState()
        return
    }
    state.eventDeliveryInFlight = true
    state.pendingEventDelivery = envelope
    state.pendingEventAttempt = attempt
    try {
        asynchttpPost(
            "eventDeliveryResponse",
            [
                uri: safeEventCallbackUrl(settings?.customEventCallbackUrl),
                headers: [Authorization: "Bearer ${state.accessToken}", "Content-Type": "application/json"],
                body: body,
                timeout: 5,
                followRedirects: false
            ],
            [delivery_id: envelope.delivery_id]
        )
    } catch (Exception ignored) {
        state.eventDeliveryInFlight = false
        scheduleEventDeliveryRetry(attempt)
    }
}

def eventDeliveryResponse(response, data) {
    state.eventDeliveryInFlight = false
    int attempt = state.pendingEventAttempt instanceof Number ? state.pendingEventAttempt as int : 0
    Integer status = null
    try {
        status = response?.getStatus() as Integer
    } catch (Exception ignored) {
        status = null
    }
    if (status != null && status >= 200 && status <= 299) {
        clearEventDeliveryState()
        return
    }
    if (status == 429 || (status != null && status >= 500 && status <= 599)) {
        scheduleEventDeliveryRetry(attempt)
        return
    }
    incrementEventCounter("eventDeliveryDroppedCount")
    clearEventDeliveryState()
}

private void scheduleEventDeliveryRetry(int attempt) {
    if (attempt > 0 && attempt < MAX_EVENT_DELIVERY_ATTEMPTS && eventDeliveryConfigured()) {
        runIn(attempt == 1 ? 2 : 4, "retryPendingEventDelivery", [overwrite: true])
    } else {
        incrementEventCounter("eventDeliveryDroppedCount")
        clearEventDeliveryState()
    }
}

def retryPendingEventDelivery() {
    Map envelope = state.pendingEventDelivery instanceof Map ? state.pendingEventDelivery as Map : null
    int attempt = state.pendingEventAttempt instanceof Number ? state.pendingEventAttempt as int : 0
    state.eventDeliveryInFlight = false
    if (envelope == null || attempt >= MAX_EVENT_DELIVERY_ATTEMPTS || !eventDeliveryConfigured()) {
        clearEventDeliveryState()
        return
    }
    if (!admitEventDeliveryRate()) {
        incrementEventCounter("eventDeliveryDroppedCount")
        clearEventDeliveryState()
        return
    }
    deliverEventEnvelope(envelope, attempt + 1)
}

def topology() {
    Map selection = selectedDeviceSelection()
    if (!deviceScopeSelected(selection, "topology")) {
        return discoveryEnvelope(
            "topology",
            "topology",
            "selected-automation-context",
            selection,
            [],
            false,
            [ownerSelectionLimitation("topology")]
        )
    }
    String observedAt = isoTime()
    String coverageId = coverageIdFor("topology")
    Map roomContexts = readDeviceRoomContexts(
        (selection.devices as List).take(MAX_TOPOLOGY_DEVICES),
        "topology",
        "selected-automation-context"
    )
    Map identityContexts = readSelectedDeviceIdentityContexts(
        (selection.devices as List).take(MAX_TOPOLOGY_DEVICES),
        "topology",
        "selected-automation-context"
    )
    List<Map> records = topologyRecords(
        selection,
        observedAt,
        coverageId,
        roomContexts.by_native_id as Map,
        identityContexts.by_native_id as Map
    )
    List<Map> limitations = topologyLimitations(selection)
    limitations.addAll(roomContexts.limitations as List<Map>)
    limitations.addAll(identityContexts.limitations as List<Map>)
    return discoveryEnvelope(
        "topology",
        "topology",
        "selected-automation-context",
        selection,
        records,
        true,
        limitations
    )
}

def exposure() {
    Map selection = selectedDeviceSelection()
    if (!deviceScopeSelected(selection, "exposure")) {
        return discoveryEnvelope(
            "exposure",
            "exposure",
            "connector-selection",
            selection,
            [],
            false,
            [ownerSelectionLimitation("exposure")]
        )
    }
    String observedAt = isoTime()
    String coverageId = coverageIdFor("exposure")
    List<Map> records = selection.devices.collect { device -> exposureRecord(device, observedAt, coverageId) }
    return discoveryEnvelope("exposure", "exposure", "connector-selection", selection, records, true, [])
}

private Map evidenceCategoryOptions() {
    return [
        inventory: "Selected-device inventory and native capabilities",
        state: "Current selected-device attribute state",
        events: "Bounded recent selected-device event evidence",
        modes: "Hub modes and the currently observed mode",
        health: "Safely available selected-device health status",
        exposure: "Connector selection exposure records",
        command_metadata: "Inert supported-command names (never callable)",
        topology: "Connector-app and owner-selected device topology"
    ]
}

private Map eventAttributeOptions() {
    return [
        acceleration: "Acceleration",
        battery: "Battery",
        contact: "Contact",
        humidity: "Humidity",
        illuminance: "Illuminance",
        level: "Level",
        lock: "Lock state (observation only)",
        motion: "Motion",
        presence: "Presence",
        smoke: "Smoke",
        switch: "Switch state",
        temperature: "Temperature",
        water: "Water"
    ]
}

private Map selectedEventSubscriptionSelection() {
    boolean customProfile = activeScopeProfile() == "custom"
    Map discoverySelection = selectedDeviceSelection()
    Set<String> admittedDiscoveryIds = (discoverySelection.devices as List).collect { device -> device.id.toString() } as Set
    Object selected = settings?.customEventDevices
    Collection requested = selected instanceof Collection ? selected as Collection : selected ? [selected] : []
    List requestedDevices = requested.findAll { device -> device != null }
        .unique { device -> device.id.toString() }
    List devices = requestedDevices.findAll { device ->
        device != null && admittedDiscoveryIds.contains(device.id.toString())
    }.sort { left, right -> left.id.toString() <=> right.id.toString() }
    Object selectedAttributes = settings?.customEventAttributes
    List<String> rawAttributes = selectedAttributes instanceof Collection ? selectedAttributes as List :
        selectedAttributes ? [selectedAttributes.toString()] : []
    Set<String> allowed = eventAttributeOptions().keySet()
    List<String> attributes = rawAttributes.collect { value -> value.toString() }
        .findAll { value -> allowed.contains(value) }
        .unique()
        .sort()
    boolean enabled = customProfile &&
        settings?.customEventSubscriptionsEnabled == true &&
        evidenceSelected("events") &&
        !devices.isEmpty() &&
        !attributes.isEmpty()
    return [
        enabled: enabled,
        devices: devices.take(MAX_EVENT_SUBSCRIPTION_DEVICES),
        attributes: attributes.take(MAX_EVENT_SUBSCRIPTION_ATTRIBUTES),
        device_overflow: devices.size() > MAX_EVENT_SUBSCRIPTION_DEVICES,
        attribute_overflow: attributes.size() > MAX_EVENT_SUBSCRIPTION_ATTRIBUTES,
        scope_rejected: requestedDevices.size() != devices.size() || rawAttributes.size() != attributes.size()
    ]
}

private List<String> selectedEvidenceCategories() {
    String profile = activeScopeProfile()
    if (profile == "safe_empty") {
        return []
    }
    if (profile == "minimal_polling") {
        return ["inventory", "state"]
    }
    List<String> approved = state.approvedCustomEvidence instanceof Collection ?
        state.approvedCustomEvidence as List<String> : []
    Set<String> allowed = evidenceCategoryOptions().keySet()
    return approved.collect { String category -> category.toString() }
        .findAll { String category -> allowed.contains(category) }
        .unique()
        .sort()
}

private List<String> candidateCustomEvidenceCategories() {
    return evidenceCategoryOptions().keySet().findAll { String category ->
        String settingName = "customEvidence_${category}".toString()
        settings[settingName] == true
    }.collect { String category -> category }.sort()
}

private String activeScopeProfile() {
    Object selected = settings?.scopeProfile
    String profile = selected instanceof CharSequence ? selected.toString() : "safe_empty"
    String active = profile == "safe_empty" || profile == "minimal_polling" || profile == "custom" ?
        profile : "safe_empty"
    reconcileScopeProfileAuthority(active)
    return active
}

private boolean evidenceSelected(String category) {
    return selectedEvidenceCategories().contains(category)
}

private boolean deviceScopeSelected(Map selection, String category) {
    return evidenceSelected(category) && (selection.admitted_count as int) > 0
}

private List<Map> eventHistory() {
    return admittedEventHistory().sort { left, right ->
        int timeOrder = left.source_event_at.toString() <=> right.source_event_at.toString()
        return timeOrder != 0 ? timeOrder : left.record_id.toString() <=> right.record_id.toString()
    }
}

private List<Map> admittedEventHistory() {
    List raw = state.eventHistory instanceof List ? state.eventHistory as List : []
    return raw.findAll { item -> item instanceof Map && item.kind == "hubitat.event" }
        .takeRight(MAX_EVENT_HISTORY)
}

private Map boundedEventRequestWindow() {
    String fromText = params?.from instanceof CharSequence ? params.from.toString() : null
    String toText = params?.to instanceof CharSequence ? params.to.toString() : null
    Date fromDate = parseEventTime(fromText)
    Date toDate = parseEventTime(toText)
    if (fromDate == null || toDate == null || toDate.before(fromDate) || toDate.time - fromDate.time > 86400000L) {
        String refusedAt = isoTime()
        return [
            valid: false,
            from: refusedAt,
            to: refusedAt,
            limitation: [
                code: "events.window-invalid",
                message: "The requested event window was missing, malformed, reversed, or exceeded 24 hours.",
                affected_scope: "events:selected-devices",
                retryable: false,
                evidence_refs: []
            ]
        ]
    }
    return [valid: true, from: isoTime(fromDate), to: isoTime(toDate)]
}

private Date parseEventTime(String value) {
    if (value == null || value.length() > 32) {
        return null
    }
    for (String format in ["yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX"]) {
        try {
            return Date.parse(format, value)
        } catch (Exception ignored) {
            // Try only the other closed RFC3339 UTC shape.
        }
    }
    return null
}

private int boundedEventLimit() {
    try {
        int requested = params?.limit == null ? 1000 : params.limit.toString().toInteger()
        return Math.max(1, Math.min(1000, requested))
    } catch (Exception ignored) {
        return 1000
    }
}

private Object safeEventScalar(Object value) {
    if (value instanceof Boolean) {
        return value
    }
    if (value instanceof Number) {
        try {
            BigDecimal numeric = new BigDecimal(value.toString())
            return numeric >= -1000000000G && numeric <= 1000000000G ? value : null
        } catch (NumberFormatException ignored) {
            return null
        }
    }
    if (!(value instanceof CharSequence)) {
        return null
    }
    String text = value.toString()
    if (!text || text.length() > MAX_TEXT_LENGTH || text ==~ /(?i).*(https?:\/\/|data:|authorization|bearer|credential|password|token|secret|base64|image|video|audio).*/) {
        return null
    }
    if (text ==~ /[A-Za-z0-9_+\/=]{32,}/ || text ==~ /(?i)[a-f0-9]{40,128}/) {
        return null
    }
    return text
}

private String safeEventCallbackUrl(Object raw) {
    String value = raw instanceof CharSequence ? raw.toString() : ""
    if (value.length() > 256 || value.contains("@") || value.contains("?") || value.contains("#")) {
        return null
    }
    def match = value =~ /^https:\/\/(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})(?::(\d{1,5}))?\/v1\/hubitat\/events$/
    if (!match.matches()) {
        return null
    }
    List<Integer> octets = (1..4).collect { int index -> match[0][index].toString().toInteger() }
    if (octets.any { int octet -> octet < 0 || octet > 255 }) {
        return null
    }
    Integer port = match[0][5] == null ? null : match[0][5].toString().toInteger()
    if (port != null && (port < 1 || port > 65535)) {
        return null
    }
    boolean privateAddress = octets[0] == 10 || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31) ||
        (octets[0] == 192 && octets[1] == 168)
    if (!privateAddress) {
        return null
    }
    return value
}

private Map outboundEventRecord(Map record) {
    return [
        kind: "hubitat.event",
        record_id: record.record_id,
        native_id: record.native_id,
        subject_ref: record.subject_ref,
        name: record.name,
        value: record.value,
        unit: record.unit,
        source_event_at: record.source_event_at,
        observed_at: record.observed_at,
        sequence: record.sequence,
        event_kind: record.event_kind,
        invocable: false,
        provenance_refs: record.provenance_refs,
        coverage_ref: record.coverage_ref
    ]
}

private Map selectedDeviceSelection() {
    if (activeScopeProfile() == "safe_empty") {
        return [devices: [], selected_count: 0, admitted_count: 0, overflow: false]
    }
    Object selected = settings?.profileSelectedDevices
    Collection devices = selected instanceof Collection ? selected as Collection : selected ? [selected] : []
    List admitted = []
    int selectedCount = 0
    for (device in devices) {
        if (device != null) {
            selectedCount += 1
            if (admitted.size() < MAX_SELECTED_DEVICES) {
                admitted.add(device)
            }
        }
    }
    admitted.sort { left, right -> left.id.toString() <=> right.id.toString() }
    return [
        devices: admitted,
        selected_count: selectedCount,
        admitted_count: admitted.size(),
        overflow: selectedCount > admitted.size()
    ]
}

private List<Map> routeManifest() {
    return [
        routeDeclaration("metadata", "/v1/metadata", []),
        routeDeclaration("capabilities", "/v1/capabilities", []),
        routeDeclaration("inventory", "/v1/inventory", ["cursor"]),
        routeDeclaration("state", "/v1/state", ["cursor"]),
        routeDeclaration("events", "/v1/events", ["from", "to", "cursor", "limit"]),
        routeDeclaration("topology", "/v1/topology", ["cursor"]),
        routeDeclaration("exposure", "/v1/exposure", ["cursor"])
    ]
}

private Map connectorManifest() {
    return [
        manifest_version: "1.0.0",
        connector_id: CONNECTOR_ID,
        routes: routeManifest(),
        limits: connectorLimits()
    ]
}

private Map routeDeclaration(String operation, String fixedPath, List<String> parameters) {
    return [
        operation: operation,
        method: "GET",
        path: fixedPath,
        request_parameters: parameters,
        response_schema: "homescope.hubitat.discovery-envelope@1.0.0"
    ]
}

private Map connectorLimits() {
    return [
        max_in_flight: 1,
        request_deadline_ms: 5000,
        max_response_bytes: MAX_PAGE_BYTES,
        max_records_per_page: MAX_PAGE_RECORDS,
        max_pages_per_snapshot: MAX_SNAPSHOT_PAGES,
        max_event_window_seconds: 86400,
        max_events_per_run: 1000
    ]
}

private Map deviceRecord(device, String observedAt, String coverageId, Map roomContext, Map identityContext) {
    String deviceId = safeId(device.id, "device")
    List<Map> capabilityRecords = capabilityRecords(device, observedAt, coverageId)
    List<String> capabilityRefs = capabilityRecords.collect { Map record -> record.record_id }
    List<String> attributeRefs = evidenceSelected("state") ? supportedAttributes(device).collect { attribute ->
        recordId("attribute", "${deviceId}.${safeId(attribute.name, 'attribute')}")
    } : []
    Map record = baseRecord("hubitat.device", "device.${deviceId}", device.id, observedAt, coverageId)
    record.label = boundedText(readDeviceProperty(device, "label"))
    record.name = boundedText(readDeviceProperty(device, "name"))
    record.type_name = boundedText(readDeviceProperty(device, "typeName"))
    record.manufacturer = readDeviceIdentityText(device, "manufacturerName", "manufacturer")
    record.model = readDeviceIdentityText(device, "modelName", "model")
    record.network_id = identityContext.network_id
    record.lifecycle = "present"
    record.room = roomContext.room
    record.parent_ref = identityContext.parent_ref
    record.child_refs = identityContext.child_refs
    record.capability_refs = capabilityRefs
    record.attribute_refs = attributeRefs
    record.command_metadata = evidenceSelected("command_metadata") ? inertCommandMetadata(device) : []
    return record
}

private List<Map> capabilityRecords(device, String observedAt, String coverageId) {
    String deviceId = safeId(device.id, "device")
    return deviceCapabilities(device).collect { capability ->
        String nativeName = boundedText(capability.name) ?: "unknown"
        Map record = baseRecord(
            "hubitat.capability",
            "capability.${deviceId}.${safeId(nativeName, 'unknown')}",
            nativeName,
            observedAt,
            coverageId
        )
        record.native_name = nativeName
        record.shared_meaning = null
        return record
    }
}

private List<Map> attributeRecords(device, String observedAt, String coverageId) {
    String deviceId = safeId(device.id, "device")
    Map<String, Object> statesByName = [:]
    currentStates(device).each { current ->
        String name = boundedText(current.name)
        if (name && !statesByName.containsKey(name)) {
            statesByName[name] = current
        }
    }
    return supportedAttributes(device).collect { attribute ->
        String nativeName = boundedText(attribute.name) ?: "unknown"
        Object current = statesByName[nativeName]
        Object currentValue = current == null ? null : readDeviceProperty(current, "value")
        String availability = current == null ? "missing" : currentValue == null ? "unavailable" :
            currentValue instanceof CharSequence && currentValue.toString().isEmpty() ? "known-empty" : "reported"
        Map record = baseRecord(
            "hubitat.attribute",
            "attribute.${deviceId}.${safeId(nativeName, 'unknown')}",
            "${device.id}:${nativeName}",
            observedAt,
            coverageId
        )
        record.subject_ref = recordId("device", deviceId)
        record.native_name = nativeName
        record.availability = availability
        if (current != null) {
            record.value = boundedScalar(currentValue)
            record.unit = boundedText(readDeviceProperty(current, "unit"))
            record.source_event_at = isoTimeOrNull(readDeviceProperty(current, "date"))
        }
        return record
    }
}

private List<Map> modeRecords(String observedAt, String coverageId) {
    String currentModeName = readLocationModeName()
    List modes = readDeviceProperty(location, "modes") instanceof Collection ? readDeviceProperty(location, "modes") : []
    return modes.findAll { mode -> mode != null }
        .sort { left, right -> left.id.toString() <=> right.id.toString() }
        .take(128)
        .collect { mode ->
            String nativeId = mode.id.toString()
            String nativeName = boundedText(readDeviceProperty(mode, "name"))
            Map record = baseRecord(
                "hubitat.mode",
                "mode.${safeId(nativeId, 'unknown')}",
                nativeId,
                observedAt,
                coverageId
            )
            record.name = nativeName ?: "Unknown"
            record.current = currentModeName == null || nativeName == null ? null : currentModeName == nativeName
            return record
        }
}

private String readLocationModeName() {
    try {
        return boundedText(location.getMode())
    } catch (Exception ignored) {
        return null
    }
}

private Map healthRecord(device, String observedAt, String coverageId) {
    String deviceId = safeId(device.id, "device")
    String nativeStatus = boundedText(readDeviceProperty(device, "status"))
    String normalized = nativeStatus?.toLowerCase()
    String status = normalized == "active" || normalized == "online" ? "available" :
        normalized == "inactive" || normalized == "offline" ? "unavailable" : "unknown"
    Map record = baseRecord("hubitat.health", "health.${deviceId}", device.id, observedAt, coverageId)
    record.subject_ref = recordId("device", deviceId)
    record.status = status
    record.basis = boundedText(
        nativeStatus ? "Hubitat device status: ${nativeStatus}" : "Hubitat device status unavailable"
    )
    return record
}

private List<Map> topologyRecords(
    Map selection,
    String observedAt,
    String coverageId,
    Map roomContexts,
    Map identityContexts
) {
    List devices = (selection.devices as List).take(MAX_TOPOLOGY_DEVICES)
    String appNativeId = boundedText(app.id)
    String appId = safeId(app.id, "app")
    String appRef = recordId("automation", "app.${appId}")
    List<Map> relationships = []

    devices.each { device ->
        String deviceId = safeId(device.id, "device")
        relationships.addAll(topologyRelationshipPair(
            "app.${appId}.references.device.${deviceId}",
            "device.${deviceId}.referenced-by.app.${appId}",
            appRef,
            "automation-object",
            recordId("device", deviceId),
            "device",
            "references",
            "referenced-by",
            "The connector app explicitly selected this device for observation.",
            "connector-selected-device",
            observedAt,
            coverageId
        ))
    }

    devices.each { device ->
        Map identityContext = identityContexts[device.id.toString()] as Map
        String parentRef = identityContext.parent_ref
        if (parentRef != null) {
            String childId = safeId(device.id, "device")
            String parentId = parentRef.replaceFirst(/^hubitat\.device\./, "")
            relationships.addAll(topologyRelationshipPair(
                "device.${childId}.child-of.device.${parentId}",
                "device.${parentId}.contains.device.${childId}",
                recordId("device", childId),
                "device",
                recordId("device", parentId),
                "device",
                "member-of",
                "contains",
                "A selected device reported this selected parent-device relationship.",
                "selected-device-parent-child",
                observedAt,
                coverageId
            ))
        }
    }

    List<String> appRelationshipRefs = relationships.findAll { Map relationship ->
        relationship.from_ref == appRef
    }.collect { Map relationship -> relationship.record_id }
    List<Map> records = [connectorAppTopologyRecord(
        appNativeId,
        appId,
        appRelationshipRefs,
        observedAt,
        coverageId
    )]
    records.addAll(devices.collect { device ->
        topologyDeviceRecord(
            device,
            observedAt,
            coverageId,
            roomContexts[device.id.toString()] as Map,
            identityContexts[device.id.toString()] as Map
        )
    })
    records.addAll(relationships)
    records.addAll(topologyCapabilityGapRecords(observedAt, coverageId))
    return records
}

private Map connectorAppTopologyRecord(
    String appNativeId,
    String appId,
    List<String> relationshipRefs,
    String observedAt,
    String coverageId
) {
    Map record = baseRecord("hubitat.automation-object", "automation.app.${appId}", app.id, observedAt, coverageId)
    record.object_kind = "app"
    record.label = null
    record.owner_refs = []
    record.ownership_status = "not-applicable"
    record.source_scope = [service: "topology", native_category: "connector-app-instance", object_ids: [appNativeId]]
    record.lifecycle = "present"
    record.enablement = "unknown"
    record.relationship_refs = relationshipRefs
    record.contradictions = []
    return record
}

private Map topologyDeviceRecord(
    device,
    String observedAt,
    String coverageId,
    Map roomContext,
    Map identityContext
) {
    String deviceId = safeId(device.id, "device")
    Map record = baseRecord("hubitat.device", "device.${deviceId}", device.id, observedAt, coverageId)
    record.label = boundedText(readDeviceProperty(device, "label"))
    record.name = boundedText(readDeviceProperty(device, "name"))
    record.type_name = boundedText(readDeviceProperty(device, "typeName"))
    record.manufacturer = readDeviceIdentityText(device, "manufacturerName", "manufacturer")
    record.model = readDeviceIdentityText(device, "modelName", "model")
    record.network_id = identityContext.network_id
    record.lifecycle = "present"
    record.room = roomContext.room
    record.parent_ref = identityContext.parent_ref
    record.child_refs = identityContext.child_refs
    record.capability_refs = []
    record.attribute_refs = []
    record.command_metadata = []
    return record
}

private List<Map> topologyRelationshipPair(
    String forwardSuffix,
    String reverseSuffix,
    String fromRef,
    String fromKind,
    String toRef,
    String toKind,
    String forwardKind,
    String reverseKind,
    String nativeMeaning,
    String nativeCategory,
    String observedAt,
    String coverageId
) {
    String forwardRef = recordId("relationship", forwardSuffix)
    String reverseRef = recordId("relationship", reverseSuffix)
    Map sourceScope = [service: "topology", native_category: nativeCategory]
    Map forward = topologyRelationshipRecord(
        forwardRef, fromRef, fromKind, toRef, toKind, forwardKind, reverseRef,
        nativeMeaning, sourceScope, observedAt, coverageId
    )
    Map reverse = topologyRelationshipRecord(
        reverseRef, toRef, toKind, fromRef, fromKind, reverseKind, forwardRef,
        nativeMeaning, sourceScope, observedAt, coverageId
    )
    return [forward, reverse]
}

private Map topologyRelationshipRecord(
    String relationshipId,
    String fromRef,
    String fromKind,
    String toRef,
    String toKind,
    String relationshipKind,
    String reciprocalRef,
    String nativeMeaning,
    Map sourceScope,
    String observedAt,
    String coverageId
) {
    Map record = baseRecord("hubitat.relationship", relationshipId, null, observedAt, coverageId)
    record.record_id = relationshipId
    record.from_ref = fromRef
    record.from_kind = fromKind
    record.to_ref = toRef
    record.to_kind = toKind
    record.relationship_kind = relationshipKind
    record.reciprocal_ref = reciprocalRef
    record.native_meaning = nativeMeaning
    record.source_scope = sourceScope
    record.lifecycle = "present"
    record.invocable = false
    record.contradictions = []
    record.limitations = []
    return record
}

private List<Map> topologyCapabilityGapRecords(String observedAt, String coverageId) {
    List<Map> definitions = [
        ["administrative-pages", "inherent"],
        ["complete-installed-app-export", "connector"],
        ["rule-machine-settings", "connector"],
        ["rule-machine-legacy-settings", "connector"],
        ["visual-rule-builder-settings", "connector"],
        ["app-settings", "connector"]
    ]
    return definitions.collect { List<String> definition ->
        String requested = definition[0]
        Map record = baseRecord("hubitat.capability-gap", "gap.topology.${requested}", null, observedAt, coverageId)
        record.record_id = "hubitat.gap.topology.${requested}"
        record.service = "topology"
        record.requested_scope = "topology:${requested}"
        record.status = "partially-supported"
        record.duration_class = definition[1]
        record.affected_use_cases = ["native-automation-review"]
        record.native_extensions = [origin: "feature007-policy", requirement_refs: ["FR-029", "FR-030"]]
        return record
    }
}

private List<Map> topologyLimitations(Map selection) {
    List<Map> limitations = [[
        code: "topology.scope-limited",
        message: "Only this connector app, owner-selected devices, and explicitly reported selected-device " +
            "relationships are visible; administrative pages and other app or rule definitions remain unavailable.",
        affected_scope: "topology:selected-automation-context",
        retryable: false,
        evidence_refs: []
    ]]
    if ((selection.admitted_count as int) > MAX_TOPOLOGY_DEVICES) {
        limitations.add([
            code: "topology.device-bound",
            message: "Selected devices beyond the fixed topology graph bound were not serialized so every " +
                "endpoint, reciprocal relationship, and capability gap remains on one valid page.",
            affected_scope: "topology:selected-automation-context",
            retryable: false,
            evidence_refs: []
        ])
    }
    return limitations
}

private Object parentNativeId(device) {
    return readDeviceProperty(device, "parentDeviceId")
}

private Map exposureRecord(device, String observedAt, String coverageId) {
    String deviceId = safeId(device.id, "device")
    Map record = baseRecord("hubitat.exposure", "exposure.${deviceId}", device.id, observedAt, coverageId)
    record.subject_ref = recordId("device", deviceId)
    record.exposure_kind = "selected-for-homescope-read-connector"
    record.destination = "homescope"
    record.accepted_by_destination = null
    return record
}

private List<Map> inertCommandMetadata(device) {
    Object raw = readDeviceProperty(device, "supportedCommands")
    List commands = raw instanceof Collection ? raw as List : []
    return commands.findAll { command -> command != null }
        .collect { command -> boundedText(readDeviceProperty(command, "name")) }
        .findAll { String name -> name != null }
        .unique()
        .sort()
        .take(MAX_COMMANDS_PER_DEVICE)
        .collect { String name -> [name: name, invocable: false] }
}

private List deviceCapabilities(device) {
    Object raw = readDeviceProperty(device, "capabilities")
    List capabilities = raw instanceof Collection ? raw as List : []
    return capabilities.findAll { capability -> capability != null && capability.name != null }
        .sort { left, right -> left.name.toString() <=> right.name.toString() }
        .take(MAX_CAPABILITIES_PER_DEVICE)
}

private List supportedAttributes(device) {
    Object raw = readDeviceProperty(device, "supportedAttributes")
    List attributes = raw instanceof Collection ? raw as List : []
    return attributes.findAll { attribute -> attribute != null && attribute.name != null }
        .sort { left, right -> left.name.toString() <=> right.name.toString() }
        .take(MAX_ATTRIBUTES_PER_DEVICE)
}

private List currentStates(device) {
    Object raw = readDeviceProperty(device, "currentStates")
    List states = raw instanceof Collection ? raw as List : []
    return states.findAll { current -> current != null }
        .sort { left, right -> left.name.toString() <=> right.name.toString() }
        .take(MAX_ATTRIBUTES_PER_DEVICE)
}

private String readDeviceData(device, String name) {
    try {
        return boundedText(device.getDataValue(name))
    } catch (Exception ignored) {
        return null
    }
}

private String readDeviceIdentityText(device, String propertyName, String dataName) {
    String reported = boundedText(readDeviceProperty(device, propertyName))
    return reported ?: readDeviceData(device, dataName)
}

private Map readSelectedDeviceIdentityContexts(List devices, String service, String nativeCategory) {
    Map<String, String> recordRefsByNativeId = [:]
    devices.each { device ->
        String nativeId = device.id.toString()
        recordRefsByNativeId[nativeId] = recordId("device", safeId(nativeId, "device"))
    }

    Map<String, Map> byNativeId = [:]
    int omittedNetworkIdCount = 0
    devices.each { device ->
        String nativeId = device.id.toString()
        String networkId = safeNetworkIdentifier(readDeviceProperty(device, "deviceNetworkId"))
        if (networkId == null) {
            omittedNetworkIdCount += 1
        }
        Object reportedParent = parentNativeId(device)
        String parentNativeId = reportedParent == null ? null : reportedParent.toString()
        String parentRef = parentNativeId != null && parentNativeId != nativeId ?
            recordRefsByNativeId[parentNativeId] : null
        byNativeId[nativeId] = [
            network_id: networkId,
            parent_ref: parentRef,
            child_refs: []
        ]
    }

    byNativeId.each { String childNativeId, Map context ->
        String parentRef = context.parent_ref
        if (parentRef != null) {
            String parentNativeId = recordRefsByNativeId.find { String key, String value -> value == parentRef }?.key
            if (parentNativeId != null) {
                (byNativeId[parentNativeId].child_refs as List<String>).add(recordRefsByNativeId[childNativeId])
            }
        }
    }
    byNativeId.values().each { Map context ->
        context.child_refs = (context.child_refs as List<String>).unique().sort().take(MAX_DEVICE_CHILD_REFS)
    }

    List<Map> limitations = []
    if (omittedNetworkIdCount > 0) {
        limitations.add([
            code: "network-id.not-admitted",
            message: "${omittedNetworkIdCount} selected device network identifiers were missing or did not pass " +
                "the fixed safe-identifier policy; their network_id fields remain null.",
            affected_scope: "${service}:${nativeCategory}:device-network-id-fields",
            retryable: false,
            evidence_refs: []
        ])
    }
    return [by_native_id: byNativeId, limitations: limitations]
}

private String safeNetworkIdentifier(Object value) {
    if (!(value instanceof CharSequence)) {
        return null
    }
    String candidate = value.toString()
    if (candidate.size() > MAX_SAFE_NETWORK_ID_LENGTH ||
        !(candidate ==~ /^(?:[0-9A-F]{2}|[0-9A-F]{4})$/)) {
        return null
    }
    return candidate
}

private Map readDeviceRoomContexts(List devices, String service, String nativeCategory) {
    Map<String, Map> byNativeId = [:]
    List<Map> limitations = []
    devices.each { device ->
        Map roomContext = readDeviceRoomContext(device, service, nativeCategory)
        byNativeId[device.id.toString()] = roomContext
        if (roomContext.limitation != null) {
            limitations.add(roomContext.limitation as Map)
        }
    }
    List<Map> boundedLimitations = limitations.take(MAX_ROOM_DETAIL_LIMITATIONS)
    int omittedCount = limitations.size() - boundedLimitations.size()
    if (omittedCount > 0) {
        boundedLimitations.add([
            code: "room.detail-bound",
            message: "Exact room limitations for ${omittedCount} additional selected devices were summarized " +
                "under the fixed detail bound; their null room fields remain explicit.",
            affected_scope: "${service}:${nativeCategory}:remaining-device-room-fields",
            retryable: null,
            evidence_refs: []
        ])
    }
    return [by_native_id: byNativeId, limitations: boundedLimitations]
}

private Map readDeviceRoomContext(device, String service, String nativeCategory) {
    String deviceId = safeId(device.id, "device")
    try {
        Object rawRoom = device.getRoomName()
        String room = rawRoom instanceof CharSequence ? boundedText(rawRoom) : null
        if (room != null && room.trim()) {
            return [room: room, limitation: null]
        }
        return [
            room: null,
            limitation: [
                code: "room.unknown",
                message: "Hubitat did not report an unambiguous room for this selected device.",
                affected_scope: "${service}:${nativeCategory}:device.${deviceId}:room",
                retryable: false,
                evidence_refs: []
            ]
        ]
    } catch (Exception ignored) {
        return [
            room: null,
            limitation: [
                code: "room.unavailable",
                message: "Hubitat room lookup was unavailable for this selected device.",
                affected_scope: "${service}:${nativeCategory}:device.${deviceId}:room",
                retryable: true,
                evidence_refs: []
            ]
        ]
    }
}

private Object readDeviceProperty(target, String propertyName) {
    if (target == null) {
        return null
    }
    try {
        switch (propertyName) {
            case "capabilities": return target.capabilities
            case "currentStates": return target.currentStates
            case "date": return target.date
            case "deviceNetworkId": return target.deviceNetworkId
            case "label": return target.label
            case "manufacturerName": return target.manufacturerName
            case "modes": return target.modes
            case "modelName": return target.modelName
            case "name": return target.name
            case "parentDeviceId": return target.parentDeviceId
            case "status": return target.status
            case "supportedAttributes": return target.supportedAttributes
            case "supportedCommands": return target.supportedCommands
            case "typeName": return target.typeName
            case "unit": return target.unit
            case "value": return target.value
            default: return null
        }
    } catch (Exception ignored) {
        return null
    }
}

private List<Map> capabilityCoverageResults(Map selection, String assessedAt) {
    boolean hasDevices = (selection.admitted_count as int) > 0
    boolean inventorySelected = hasDevices && evidenceSelected("inventory")
    boolean deviceStateSelected = hasDevices && evidenceSelected("state")
    boolean modesSelected = evidenceSelected("modes")
    boolean stateSelected = deviceStateSelected || modesSelected
    boolean stateComplete = deviceStateSelected && modesSelected
    List<String> stateFields = []
    if (deviceStateSelected) {
        stateFields.add("attributes")
    }
    if (modesSelected) {
        stateFields.add("modes")
    }
    boolean topologySelected = hasDevices && evidenceSelected("topology")
    boolean healthSelected = hasDevices && evidenceSelected("health")
    boolean exposureSelected = hasDevices && evidenceSelected("exposure")
    boolean identitySelected = inventorySelected
    List<Map> rows = []

    rows.add(capabilityCoverageResult(
        "metadata", "connector", selection, assessedAt, false, "supported", "complete", "records",
        ["connector-version", "contract-version", "route-manifest"], []
    ))
    rows.add(selectedCapabilityCoverage(
        "inventory", "selected-devices", selection, assessedAt, inventorySelected, true, "supported", "complete",
        ["devices", "capabilities", "room"], null, null
    ))
    rows.add(selectedCapabilityCoverage(
        "state", "selected-current-state-and-modes", selection, assessedAt, stateSelected, deviceStateSelected,
        stateComplete ? "supported" : "partially-supported", stateComplete ? "complete" : "partial",
        stateFields, stateComplete ? null : "state.scope-limited",
        stateComplete ? null : "Both selected-device state and Hub modes are required for complete state coverage."
    ))
    rows.add(selectedCapabilityCoverage(
        "events", "selected-devices", selection, assessedAt,
        hasDevices && evidenceSelected("events"), true, "partially-supported", "partial",
        ["selected-device-events", "bounded-history", "optional-outbound-delivery"],
        "events.bounded-retention",
        "Only bounded owner-selected subscription history is available; polling remains the recovery path."
    ))
    rows.add(selectedCapabilityCoverage(
        "topology", "selected-automation-context", selection, assessedAt, topologySelected, true,
        "partially-supported", "partial",
        ["connector-app", "selected-devices", "selected-relationships", "capability-gaps"],
        "topology.scope-limited",
        "Only this connector app, owner-selected devices, and explicitly reported relationships are available."
    ))
    rows.add(selectedCapabilityCoverage(
        "health", "selected-devices", selection, assessedAt, healthSelected, true, "partially-supported", "partial",
        ["device-status"], "health.diagnostics-unavailable",
        "Undocumented platform diagnostics are outside this connector's supported scope."
    ))
    rows.add(selectedCapabilityCoverage(
        "exposure", "connector-selection", selection, assessedAt, exposureSelected, true, "supported", "complete",
        ["connector-selection"], null, null
    ))
    rows.add(selectedCapabilityCoverage(
        "identity", "selected-devices", selection, assessedAt, identitySelected, true,
        "partially-supported", "partial",
        [
            "native-id", "label", "name", "room", "type", "manufacturer", "model", "capabilities",
            "safe-network-id", "selected-parent-child-references"
        ], "identity.hints-only",
        "Native identifiers and labels are identity hints, not cross-ecosystem identity proof."
    ))
    rows.add(capabilityCoverageResult(
        "credential-lifecycle", "connector-credential", selection, assessedAt, false, "unknown", "unknown",
        "not-established", [], [[
            code: "credential.checkpoint-pending",
            message: "Credential rotation and revocation require the separate owner-gated checkpoint.",
            affected_scope: "credential-lifecycle:connector-credential",
            retryable: false,
            evidence_refs: ["hubitat.connector-capabilities"]
        ]]
    ))
    return rows
}

private Map selectedCapabilityCoverage(
    String service,
    String nativeCategory,
    Map selection,
    String assessedAt,
    boolean selected,
    boolean applyDeviceBound,
    String selectedStatus,
    String selectedCompleteness,
    List<String> selectedFields,
    String limitationCode,
    String limitationMessage
) {
    if (!selected) {
        return capabilityCoverageResult(
            service, nativeCategory, selection, assessedAt, false, "unauthorized", "none", "not-established", [],
            [ownerSelectionLimitation(service)]
        )
    }

    String status = selectedStatus
    String completeness = selectedCompleteness
    Object resultMeaning = status == "supported" && completeness == "complete" ? null : "not-established"
    List<Map> limitations = []
    if (limitationCode != null && limitationMessage != null) {
        limitations.add([
            code: limitationCode,
            message: limitationMessage,
            affected_scope: "${service}:${nativeCategory}",
            retryable: false,
            evidence_refs: ["hubitat.connector-capabilities"]
        ])
    }
    if (applyDeviceBound && selection.overflow) {
        if (status == "supported") {
            status = "partially-supported"
            completeness = "partial"
            resultMeaning = "not-established"
        }
        limitations.add(selectionDeviceBoundLimitation(selection, service, nativeCategory))
    }
    return capabilityCoverageResult(
        service, nativeCategory, selection, assessedAt, true, status, completeness, resultMeaning,
        selectedFields, limitations
    )
}

private Map capabilityCoverageResult(
    String service,
    String nativeCategory,
    Map selection,
    String assessedAt,
    boolean includeSelectedObjects,
    String status,
    String completeness,
    Object resultMeaning,
    List<String> availableFields,
    List<Map> limitations
) {
    Map scope = [service: service, native_category: nativeCategory]
    if (includeSelectedObjects) {
        List<String> objectIds = selection.devices.collect { device -> safeId(device.id, "device") }
        if (objectIds) {
            scope.object_ids = objectIds
        }
    }
    Map result = [
        coverage_id: capabilityCoverageId(service),
        service: service,
        scope: scope,
        status: status,
        completeness: completeness,
        assessed_at: assessedAt,
        expires_at: isoTime(new Date(now() + 300000L)),
        available_fields: availableFields,
        bounds: connectorLimits(),
        evidence_refs: ["hubitat.connector-capabilities"],
        limitations: limitations
    ]
    if (resultMeaning != null) {
        result.result_meaning = resultMeaning
    }
    return result
}

private String capabilityCoverageId(String service) {
    if (service == "metadata") {
        return coverageIdFor("capabilities")
    }
    return safeId(
        "hubitat.coverage.capabilities.${service}.${app.id}",
        "hubitat.coverage.capabilities.${service}"
    )
}

private Object discoveryEnvelope(
    String operation,
    String service,
    String nativeCategory,
    Map selection,
    List<Map> candidateRecords,
    boolean scopeSelected,
    List<Map> initialLimitations,
    Map requestWindow = null
) {
    String generatedAt = isoTime()
    boolean pagedOperation = ["inventory", "state", "events", "topology", "exposure"].contains(operation)
    Map scope = [service: service, native_category: nativeCategory]
    List<String> objectIds = selection.devices.collect { device -> safeId(device.id, "device") }
    if (objectIds) {
        scope.object_ids = objectIds
    }
    String scopeDescriptor = cursorScopeDescriptor(operation, scope, selection)
    Map cursor = parseCursor(operation, scopeDescriptor, pagedOperation ? params?.cursor : null)
    List<Map> limitations = new ArrayList<Map>(initialLimitations)
    boolean selectionTruncated = selection.overflow as boolean
    if (selectionTruncated) {
        limitations.add(selectionDeviceBoundLimitation(selection, service, nativeCategory))
    }
    if (cursor.invalid) {
        limitations.add([
            code: "cursor.invalid",
            message: "The opaque page cursor was invalid; no caller-selected route or object was honored.",
            affected_scope: "${service}:${nativeCategory}",
            retryable: false,
            evidence_refs: []
        ])
    }
    List<Map> bounded = candidateRecords.take(MAX_SNAPSHOT_RECORDS)
    boolean recordTruncated = candidateRecords.size() > bounded.size()
    if (recordTruncated) {
        limitations.add([
            code: "snapshot.record-bound",
            message: "Records beyond the fixed snapshot bound were not serialized.",
            affected_scope: "${service}:${nativeCategory}",
            retryable: false,
            evidence_refs: []
        ])
    }

    int offset = Math.min(cursor.offset as int, bounded.size())
    List<Map> pageRecords = selectPageRecords(bounded, offset)

    while (true) {
        int nextOffset = offset + pageRecords.size()
        boolean omittedRecords = nextOffset < bounded.size()
        boolean terminalPageOmission = omittedRecords && (cursor.page as int) + 1 >= MAX_SNAPSHOT_PAGES
        boolean attemptTruncated = selectionTruncated || recordTruncated || terminalPageOmission
        boolean hasMore = omittedRecords && !terminalPageOmission
        List<Map> attemptLimitations = new ArrayList<Map>(limitations)
        if (terminalPageOmission) {
            attemptLimitations.add([
                code: "snapshot.page-bound",
                message: "Records beyond the fixed page-count bound were not serialized.",
                affected_scope: "${service}:${nativeCategory}",
                retryable: false,
                evidence_refs: []
            ])
        }
        if (!attemptTruncated) {
            attemptLimitations.add([
                code: "snapshot.best-effort",
                message: "Hubitat objects may change while this bounded read-only snapshot is serialized.",
                affected_scope: "${service}:${nativeCategory}",
                retryable: true,
                evidence_refs: []
            ])
        }
        List<Map> coverageRows = operation == "capabilities" ?
            capabilityCoverageResults(selection, generatedAt) :
            [coverageResult(
                operation,
                service,
                scope,
                scopeSelected,
                operation == "events" ? !bounded.any { Map record -> record.kind == "hubitat.event" } : bounded.isEmpty(),
                attemptTruncated,
                generatedAt,
                attemptLimitations,
                bounded
            )]
        Map response = [
            contract_version: CONTRACT_VERSION,
            connector_id: CONNECTOR_ID,
            snapshot_id: cursor.snapshot,
            operation: operation,
            scope: scope,
            generated_at: generatedAt,
            received_at: generatedAt,
            page: [
                index: cursor.page,
                next_cursor: hasMore ? "00000000-0000-4000-8000-000000000000" : null,
                record_count: pageRecords.size(),
                serialized_bytes: 0
            ],
            coverage: coverageRows,
            consistency: attemptTruncated ? "truncated" : "best-effort",
            records: pageRecords,
            limitations: attemptLimitations
        ]
        if (operation == "events" && requestWindow != null) {
            response.request_window = [from: requestWindow.from, to: requestWindow.to]
        }
        Map serialized = serializeMeasured(response)
        if ((serialized.byte_count as int) <= MAX_PAGE_BYTES) {
            if (hasMore) {
                response.page.next_cursor = issueCursor(
                    operation,
                    scopeDescriptor,
                    cursor.snapshot as String,
                    nextOffset,
                    (cursor.page as int) + 1
                )
                serialized = serializeMeasured(response)
            }
            if ((serialized.byte_count as int) <= MAX_PAGE_BYTES) {
                return render(contentType: "application/json", data: serialized.json as String)
            }
        }
        if (pageRecords.isEmpty()) {
            return renderBoundFailure()
        }
        pageRecords.remove(pageRecords.size() - 1)
    }
}

private List<Map> selectPageRecords(List<Map> records, int offset) {
    List<Map> selected = []
    int estimatedBytes = 0
    int index = offset
    while (index < records.size() && selected.size() < MAX_PAGE_RECORDS) {
        Map record = records[index]
        int recordBytes = new JsonBuilder(record).toString().getBytes("UTF-8").length
        if (!selected.isEmpty() && estimatedBytes + recordBytes > PAGE_CONTENT_BUDGET) {
            break
        }
        selected.add(record)
        estimatedBytes += recordBytes
        index += 1
    }
    return selected
}

private Map serializeMeasured(Map response) {
    String json = ""
    int measured = 0
    for (int attempt = 0; attempt < 8; attempt += 1) {
        response.page.serialized_bytes = measured
        json = new JsonBuilder(response).toString()
        int nextMeasurement = json.getBytes("UTF-8").length
        if (nextMeasurement == measured) {
            return [json: json, byte_count: measured]
        }
        measured = nextMeasurement
    }
    return [json: "", byte_count: MAX_PAGE_BYTES + 1]
}

private Object renderBoundFailure() {
    String json = new JsonBuilder([
        error: "response-bound-exceeded",
        max_response_bytes: MAX_PAGE_BYTES
    ]).toString()
    return render(status: 500, contentType: "application/json", data: json)
}

private String cursorScopeDescriptor(String operation, Map scope, Map selection) {
    return new JsonBuilder([
        operation: operation,
        scope: scope,
        selected_device_count: selection.selected_count,
        admitted_device_count: selection.admitted_count,
        selection_truncated: selection.overflow
    ]).toString()
}

private Map parseCursor(String operation, String scopeDescriptor, Object rawCursor) {
    if (rawCursor == null || rawCursor.toString().isEmpty()) {
        return [snapshot: newSnapshotId(), offset: 0, page: 0, invalid: false]
    }
    String token = rawCursor.toString()
    if (!(token ==~ /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)) {
        return [snapshot: newSnapshotId(), offset: 0, page: 0, invalid: true]
    }
    Map registry = cursorRegistry()
    Map entry = registry.remove(token) as Map
    state.cursorRegistry = registry
    long currentTimeMs = now()
    if (entry == null || entry.operation != operation || entry.scope_descriptor != scopeDescriptor ||
        !(entry.snapshot instanceof String) ||
        !(entry.snapshot ==~ /^[a-z0-9][a-z0-9._:-]{0,127}$/) ||
        !entry.snapshot.startsWith(safeId("hubitat.snapshot.${app.id}.", "hubitat.snapshot.")) ||
        !(entry.expires_at instanceof Number) || (entry.expires_at as long) < currentTimeMs ||
        !(entry.offset instanceof Number) || !(entry.page instanceof Number) ||
        (entry.offset as int) < 0 || (entry.offset as int) > MAX_SNAPSHOT_RECORDS ||
        (entry.page as int) <= 0 || (entry.page as int) >= MAX_SNAPSHOT_PAGES) {
        return [snapshot: newSnapshotId(), offset: 0, page: 0, invalid: true]
    }
    return [snapshot: entry.snapshot, offset: entry.offset as int, page: entry.page as int, invalid: false]
}

private String issueCursor(String operation, String scopeDescriptor, String snapshot, int offset, int page) {
    Map registry = cursorRegistry()
    String token = UUID.randomUUID().toString().toLowerCase()
    registry[token] = [
        operation: operation,
        scope_descriptor: scopeDescriptor,
        snapshot: snapshot,
        offset: offset,
        page: page,
        expires_at: now() + CURSOR_TTL_MS
    ]
    while (registry.size() > MAX_CURSOR_REGISTRY) {
        String oldest = registry.keySet().iterator().next() as String
        registry.remove(oldest)
    }
    state.cursorRegistry = registry
    return token
}

private Map cursorRegistry() {
    long currentTimeMs = now()
    Map raw = state.cursorRegistry instanceof Map ? state.cursorRegistry as Map : [:]
    Map active = new LinkedHashMap()
    raw.each { Object key, Object value ->
        if (key instanceof String && value instanceof Map && value.expires_at instanceof Number &&
            (value.expires_at as long) >= currentTimeMs) {
            active[key] = value
        }
    }
    return active
}

private Map coverageResult(
    String operation,
    String service,
    Map scope,
    boolean scopeSelected,
    boolean empty,
    boolean truncated,
    String assessedAt,
    List<Map> limitations,
    List<Map> boundedRecords
) {
    boolean ownerDenied = limitations.any { Map limitation -> limitation.code == "owner-scope.not-selected" }
    boolean roomLimited = operation == "inventory" && limitations.any { Map limitation ->
        limitation.code == "room.unavailable" || limitation.code == "room.unknown"
    }
    boolean identityLimited = operation == "inventory" && limitations.any { Map limitation ->
        limitation.code == "network-id.not-admitted"
    }
    boolean eventLimited = operation == "events" && limitations.any { Map limitation ->
        limitation.code == "events.bounded-retention" || limitation.code == "events.truncated"
    }
    String status = scopeSelected ? (truncated || roomLimited || identityLimited || eventLimited ? "partially-supported" : "supported") :
        ownerDenied ? "unauthorized" : "unsupported"
    String completeness = scopeSelected ? (truncated || roomLimited || identityLimited || eventLimited ? "partial" : "complete") : "none"
    String resultMeaning = scopeSelected ? (empty && !truncated ? "known-empty" :
        empty ? "not-established" : "records") : "not-established"
    if (eventLimited && empty) {
        resultMeaning = "not-established"
    }
    if (operation == "topology" && scopeSelected) {
        status = "partially-supported"
        completeness = "partial"
        resultMeaning = empty ? "not-established" : "records"
    }
    List<String> availableFields = scopeSelected ? ["native-records"] : []
    if (operation == "topology" && scopeSelected) {
        availableFields = ["connector-app", "selected-devices", "selected-relationships", "capability-gaps"]
    }
    if (scopeSelected && boundedRecords.any { Map record ->
        record.kind == "hubitat.device" && record.room != null
    }) {
        availableFields.add("room")
    }
    if (scopeSelected && boundedRecords.any { Map record ->
        record.kind == "hubitat.device" && record.network_id != null
    }) {
        availableFields.add("safe-network-id")
    }
    return [
        coverage_id: coverageIdFor(operation),
        service: service,
        scope: scope,
        status: status,
        completeness: completeness,
        result_meaning: resultMeaning,
        assessed_at: assessedAt,
        expires_at: isoTime(new Date(now() + 300000L)),
        available_fields: availableFields,
        bounds: connectorLimits(),
        evidence_refs: [],
        limitations: limitations
    ]
}

private Map ownerSelectionLimitation(String service) {
    return [
        code: "owner-scope.not-selected",
        message: "The owner has not selected the required evidence category and object scope in the local app UI.",
        affected_scope: "${service}:owner-selected",
        retryable: false,
        evidence_refs: []
    ]
}

private Map selectionDeviceBoundLimitation(Map selection, String service, String nativeCategory) {
    return [
        code: "selection.device-bound",
        message: "The owner selected ${selection.selected_count} devices; this response admits " +
            "${selection.admitted_count} under the fixed ${MAX_SELECTED_DEVICES}-device bound.",
        affected_scope: "${service}:${nativeCategory}",
        retryable: false,
        evidence_refs: []
    ]
}

private Map baseRecord(
    String kind,
    String recordSuffix,
    Object nativeId,
    String observedAt,
    String coverageId
) {
    return [
        kind: kind,
        record_id: safeId("hubitat.${recordSuffix}", "hubitat.record"),
        native_id: nativeId == null ? null : boundedText(nativeId),
        observed_at: observedAt,
        source_event_at: null,
        provenance_refs: [connectorProvenanceId()],
        coverage_ref: coverageId,
        confidence: "confirmed",
        freshness: "current"
    ]
}

private String recordId(String kind, String suffix) {
    return safeId("hubitat.${kind}.${suffix}", "hubitat.${kind}")
}

private String connectorProvenanceId() {
    return safeId("hubitat.connector.${app.id}", "hubitat.connector")
}

private String coverageIdFor(String operation) {
    return safeId("hubitat.coverage.${operation}.${app.id}", "hubitat.coverage.${operation}")
}

private String newSnapshotId() {
    return safeId("hubitat.snapshot.${app.id}.${UUID.randomUUID()}", "hubitat.snapshot")
}

private String safeId(Object raw, String fallback) {
    String value = raw == null ? "" : raw.toString().toLowerCase()
    value = value.replaceAll(/[^a-z0-9._:-]+/, "-")
    value = value.replaceAll(/^[^a-z0-9]+/, "")
    if (!value) {
        value = fallback
    }
    return value.take(128)
}

private Object boundedScalar(Object value) {
    if (value == null || value instanceof Boolean) {
        return value
    }
    if (value instanceof Number) {
        try {
            BigDecimal numeric = new BigDecimal(value.toString())
            if (numeric >= -1000000000G && numeric <= 1000000000G) {
                return value
            }
        } catch (NumberFormatException ignored) {
            // Preserve unusual native numeric vocabulary only as bounded text.
        }
    }
    return boundedText(value)
}

private String boundedText(Object value) {
    if (value == null) {
        return null
    }
    return value.toString().take(MAX_TEXT_LENGTH)
}

private String isoTimeOrNull(Object value) {
    if (value instanceof Date) {
        return isoTime(value)
    }
    return null
}

private String isoTime(Date value = new Date()) {
    return value.format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone("UTC"))
}
