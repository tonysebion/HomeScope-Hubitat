/**
 * HomeScope Observation Bridge
 *
 * Separate OAuth app for owner-registered scalar advisory observations only.
 * Projection IDs, virtual entities, attributes, and event names are fixed here.
 */
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Field
import java.io.ByteArrayOutputStream

definition(
    name: "HomeScope Observation Bridge",
    namespace: "homescope",
    author: "HomeScope",
    description: "Receives fixed, scalar, non-actuating advisory observations.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    oauth: true,
    singleInstance: true,
    singleThreaded: true
)

preferences {
    page(name: "mainPage")
}

mappings {
    path("/v1/observations") { action: [POST: "receiveObservation"] }
}

@Field static final Set<String> REQUEST_KEYS = [
    "contract_version", "projection_id", "idempotency_id", "origin", "evidence_ref", "value", "unit",
    "observed_at", "derived_at", "expires_at", "freshness", "availability", "sequence", "confidence",
    "uncertainty", "contradiction_status"
] as Set
@Field static final Set<String> ORIGIN_KEYS = ["ecosystem", "representation_id", "observation_id"] as Set
@Field static final Set<String> RETAINED_KEYS = [
    "request", "sequence", "idempotency_id", "projection_id", "received_at"
] as Set
@Field static final Set<String> FRESHNESS_VALUES = ["current", "aging", "stale", "expired", "unknown"] as Set
@Field static final Set<String> AVAILABILITY_VALUES = ["available", "unavailable", "unknown"] as Set
@Field static final Set<String> CONFIDENCE_VALUES = ["confirmed", "high", "medium", "low", "unknown"] as Set
@Field static final Set<String> CONTRADICTION_VALUES = ["none", "resolved", "open", "unknown"] as Set
@Field static final Set<String> SAFE_UNCERTAINTY_VALUES = [
    "Partial camera occlusion", "Conflicting bridge state."
] as Set
@Field static final Integer MAX_REQUEST_BYTES = 4096
@Field static final Integer MAX_CONTENT_NODES = 128
@Field static final Integer MAX_RECENT_IDEMPOTENCY = 32
@Field static final Integer MAX_TEXT_LENGTH = 128
@Field static final BigDecimal MAX_SEQUENCE = new BigDecimal("9007199254740991")
@Field static final BigDecimal MAX_NUMBER = new BigDecimal("1000000000")
@Field static final String CONTRACT_VERSION = "1.0.0"
@Field static final String COVERAGE_REF = "coverage.projection"
@Field static final java.util.regex.Pattern TYPED_ID = java.util.regex.Pattern.compile(
    "^[a-z][a-z0-9_]{1,31}:[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\$"
)
@Field static final java.util.regex.Pattern UNSAFE_TEXT = java.util.regex.Pattern.compile(
    "(?i)(?:https?://|data:|authorization|bearer|credential|password|passwd|token|secret|cookie|api[_ -]?key|" +
        "command|execute|actuate|unlock|lock(?:ed|ing)?|turn[_ -]?(?:on|off)|automation|rule|scene|mode|" +
        "configuration|configure|rename|delete|generic[_ -]?method|provider[_ -]?payload|history|" +
        "image|video|audio|thumbnail|frame|media|base64)"
)
@Field static final java.util.regex.Pattern ENCODED_TEXT = java.util.regex.Pattern.compile(
    "(?i)(?:gh[pousr]_[A-Za-z0-9_]{20,}|eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{4,}\\." +
        "[A-Za-z0-9_-]{4,}|AKIA[0-9A-Z]{16}|[A-Za-z0-9_+/=]{32,}|%[0-9A-Fa-f]{2})"
)

@Field static final Map<String, Map<String, Object>> PROJECTION_REGISTRY = [
    "virtual_sensor:55555555-5555-4555-8555-555555555555": [
        displayName: "Vehicle present advisory observation",
        dni: "homescope-observation-vehicle-present",
        driverType: "HomeScope Observation Bridge Child",
        valueType: "boolean",
        unit: null,
        sourceEcosystems: ["camera"] as Set,
        policyId: "homescope-managed-targets",
        policyVersion: "1.0.0",
        attributes: [
            kind: "observationKind",
            value: "observationValue",
            schemaVersion: "observationSchemaVersion",
            recordId: "observationRecordId",
            nativeId: "observationNativeId",
            nativeExtensions: "observationNativeExtensions",
            projectionId: "observationProjectionId",
            idempotencyId: "observationIdempotencyId",
            originEcosystem: "observationOriginEcosystem",
            originRepresentationId: "observationOriginRepresentationId",
            originObservationId: "observationOriginObservationId",
            freshness: "observationFreshness",
            availability: "observationAvailability",
            confidence: "observationConfidence",
            provenance: "observationProvenance",
            provenanceRefs: "observationProvenanceRefs",
            coverageRef: "observationCoverageRef",
            uncertainty: "observationUncertainty",
            contradiction: "observationContradiction",
            observedAt: "observationObservedAt",
            sourceEventAt: "observationSourceEventAt",
            derivedAt: "observationDerivedAt",
            receivedAt: "observationReceivedAt",
            expiresAt: "observationExpiresAt",
            sequence: "observationSequence",
            unit: "observationUnit",
            registrationPolicyId: "observationRegistrationPolicyId",
            registrationPolicyVersion: "observationRegistrationPolicyVersion",
            health: "observationHealth",
            independence: "observationIndependence",
            actionable: "observationActionable"
        ]
    ]
]

def mainPage() {
    Set<String> selected = ownerSelectedProjectionIds()
    Set<String> confirmed = confirmedProjectionIds()
    boolean selectionConfirmed = selected != null && !selected.isEmpty() && selected == confirmed
    invalidateInactiveProjectionChildren(activeProjectionRegistry())
    dynamicPage(name: "mainPage", title: "HomeScope Observation Bridge", install: true, uninstall: true) {
        section("Owner-selected advisory projections") {
            paragraph "The default is empty. Select only code-defined scalar advisory projections. " +
                "Physical devices, security devices, arbitrary keys, modes, rules, and automations are not selectable."
            input(
                name: "selectedProjectionIds",
                type: "enum",
                title: "Registered scalar advisory projections",
                description: "Changing this selection disables publication until the exact selection is confirmed.",
                options: projectionOptions(),
                multiple: true,
                required: false,
                submitOnChange: true
            )
            if (selected != null && !selected.isEmpty() && !selectionConfirmed) {
                input(
                    name: "confirmProjectionSelection",
                    type: "button",
                    title: "Confirm this exact advisory projection selection"
                )
            }
            if (selectionConfirmed) {
                paragraph "Confirmed locally: ${selected.size()} registered scalar advisory projection(s)."
            } else {
                paragraph "No advisory projection is active until the current non-empty selection is confirmed."
            }
        }
        section("Dedicated LAN-local observation credential") {
            paragraph "This credential is separate from the HomeScope read connector and grants only the fixed " +
                "registered-scalar POST route. Copy both values only into the approved local secret store."
            if (state.accessToken) {
                paragraph "HOMESCOPE_HUBITAT_OBSERVATION_URL: ${getFullLocalApiServerUrl()}"
                paragraph "HOMESCOPE_HUBITAT_OBSERVATION_TOKEN (approved local secret store only): ${state.accessToken}"
                input(
                    name: "rotateObservationAccessToken",
                    type: "button",
                    title: "Rotate the dedicated observation access token"
                )
            } else {
                paragraph "The dedicated observation token is created when this app instance is installed."
            }
        }
        section("Safety boundary") {
            paragraph "This app exposes one bounded observation publication route. It has no physical-device, " +
                "security-device, command, configuration, automation, generic proxy, or self-update authority."
        }
    }
}

def installed() {
    ensureBridgeCredential()
    initializeBridge()
}

def updated() {
    unsubscribe()
    unschedule()
    ensureBridgeCredential()
    initializeBridge()
}

def uninstalled() {
    unschedule()
    atomicState.remove("bridgeState")
    atomicState.remove("projections")
    atomicState.remove("recentPublicationHistory")
    state.remove("confirmedProjectionIds")
    if (state.accessToken) {
        revokeAccessToken()
        state.remove("accessToken")
    }
}

def appButtonHandler(String buttonName) {
    if (buttonName == "confirmProjectionSelection") {
        Set<String> selected = ownerSelectedProjectionIds()
        if (selected == null || selected.isEmpty()) {
            state.remove("confirmedProjectionIds")
            initializeBridge()
            return
        }
        state.confirmedProjectionIds = selected.toList().sort()
        initializeBridge()
        return
    }
    if (buttonName == "rotateObservationAccessToken") {
        if (state.accessToken) {
            revokeAccessToken()
            state.remove("accessToken")
        }
        ensureBridgeCredential()
    }
}

private Set<String> ownerSelectedProjectionIds() {
    Object raw = settings?.selectedProjectionIds
    if (raw == null || raw == "" || raw == []) return [] as Set
    List candidates = raw instanceof Collection ? (raw as List) : [raw]
    Set<String> selected = [] as Set
    for (Object candidate : candidates) {
        if (!(candidate instanceof String) || !PROJECTION_REGISTRY.containsKey(candidate)) return null
        if (!selected.add(candidate as String)) return null
    }
    return selected
}

private Set<String> confirmedProjectionIds() {
    Object raw = state.confirmedProjectionIds
    if (!(raw instanceof Collection)) return [] as Set
    List candidates = raw as List
    Set<String> confirmed = [] as Set
    for (Object candidate : candidates) {
        if (!(candidate instanceof String) || !PROJECTION_REGISTRY.containsKey(candidate)) return [] as Set
        if (!confirmed.add(candidate as String)) return [] as Set
    }
    return confirmed
}

private Map activeProjectionRegistry() {
    Set<String> selected = ownerSelectedProjectionIds()
    Set<String> confirmed = confirmedProjectionIds()
    if (selected == null || selected.isEmpty() || selected != confirmed) return [:]
    Map active = [:]
    selected.each { String projectionId -> active[projectionId] = PROJECTION_REGISTRY[projectionId] }
    return active
}

private Map<String, String> projectionOptions() {
    Map<String, String> options = [:]
    PROJECTION_REGISTRY.each { String projectionId, Map<String, Object> registration ->
        options[projectionId] = registration.displayName as String
    }
    return options
}

private void ensureBridgeCredential() {
    if (!state.accessToken) {
        createAccessToken()
    }
}

private void initializeBridge() {
    Map active = activeProjectionRegistry()
    invalidateInactiveProjectionChildren(active)
    active.each { String projectionId, Map<String, Object> registration ->
        String dni = registration.dni as String
        if (!getChildDevice(dni)) {
            addChildDevice(
                "homescope",
                registration.driverType as String,
                dni,
                [name: "HomeScope Advisory Observation", label: "HomeScope Advisory: ${projectionId}", isComponent: true]
            )
        }
    }
    migrateRetainedAuthority()
    runEvery1Minute("expireObservations")
}

private void invalidateInactiveProjectionChildren(Map active) {
    PROJECTION_REGISTRY.each { String projectionId, Map<String, Object> registration ->
        if (!active.containsKey(projectionId)) {
            def projection = getChildDevice(registration.dni as String)
            if (projection) failClosedActionable(projectionId, projection)
        }
    }
}

def receiveObservation() {
    invalidateInactiveProjectionChildren(activeProjectionRegistry())
    Map inbound = readInboundRequest()
    if (!inbound.accepted) {
        return renderRejection(inbound.status as Integer, inbound.reason as String)
    }
    Map body = inbound.body as Map
    Map verdict = validateRequest(body)
    if (!verdict.accepted) {
        return renderRejection(verdict.status as Integer, verdict.reason as String)
    }

    Map replayVerdict = validateOrdering(body)
    if (!replayVerdict.accepted) {
        return renderRejection(replayVerdict.status as Integer, replayVerdict.reason as String)
    }
    if (replayVerdict.replay) {
        return renderReceipt(body, replayVerdict.received_at as String)
    }

    String projectionId = body.projection_id as String
    def projection = getChildDevice(PROJECTION_REGISTRY[projectionId].dni as String)
    if (!projection) {
        return renderRejection(503, "bridge_target_unavailable")
    }
    String receivedAt = new Date().toInstant().toString()
    boolean published = publishObservation(
        projectionId, projection, body, receivedAt, verdict.actionable as Boolean
    )
    if (!published) {
        return renderRejection(503, "delivery_state_unknown")
    }

    if (!commitRetainedObservation(body, receivedAt)) {
        failClosedActionable(projectionId, projection)
        return renderRejection(503, "delivery_state_unknown")
    }
    return renderReceipt(body, receivedAt)
}

private Map readInboundRequest() {
    if (!isPrivateLanRequest()) return rejected(403, "lan_origin_required")
    try {
        String declaredLength = request.getHeader("Content-Length")
        if (!(declaredLength ==~ /^[0-9]{1,10}$/)) return rejected(411, "content_length_required")
        Long contentLength = declaredLength as Long
        if (contentLength <= 0L || contentLength > MAX_REQUEST_BYTES) {
            return rejected(413, "request_too_large")
        }
        byte[] rawBytes = readBoundedRequestBytes()
        if (rawBytes == null) return rejected(413, "request_too_large")
        if (rawBytes.length != contentLength || rawBytes.length > MAX_REQUEST_BYTES) {
            return rejected(413, "request_too_large")
        }
        String raw = new String(rawBytes, "UTF-8")
        if (!isExactUtf8RoundTrip(rawBytes, raw)) return rejected(400, "malformed_json")
        if (hasDuplicateObjectKeys(raw)) return rejected(400, "duplicate_key")
        Object decoded = new JsonSlurper().parseText(raw)
        if (!(decoded instanceof Map)) return rejected(400, "malformed_json")
        return [accepted: true, status: 200, body: decoded as Map]
    } catch (ignored) {
        return rejected(400, "malformed_json")
    }
}

private boolean isExactUtf8RoundTrip(byte[] rawBytes, String decoded) {
    if (rawBytes == null || decoded == null) return false
    byte[] encoded = decoded.getBytes("UTF-8")
    if (encoded.length != rawBytes.length) return false
    for (Integer index = 0; index < rawBytes.length; index += 1) {
        if (encoded[index] != rawBytes[index]) return false
    }
    return true
}

private byte[] readBoundedRequestBytes() {
    def input = request.getInputStream()
    if (!input) return null
    ByteArrayOutputStream bounded = new ByteArrayOutputStream(MAX_REQUEST_BYTES + 1)
    byte[] chunk = new byte[512]
    Integer total = 0
    while (total <= MAX_REQUEST_BYTES) {
        Integer remaining = (MAX_REQUEST_BYTES + 1) - total
        Integer count = input.read(chunk, 0, Math.min(chunk.length, remaining))
        if (count < 0) break
        if (count == 0) return null
        bounded.write(chunk, 0, count)
        total += count
    }
    if (total > MAX_REQUEST_BYTES) return null
    return bounded.toByteArray()
}

private boolean isPrivateLanRequest() {
    try {
        String remoteAddress = request.getRemoteAddr() as String
        String hostHeader = request.getHeader("Host") as String
        return isPrivateAddress(remoteAddress) && isPrivateHost(hostHeader)
    } catch (ignored) {
        return false
    }
}

private boolean isPrivateHost(String hostHeader) {
    if (!hostHeader) return false
    String host = hostHeader.trim().toLowerCase()
    if (host.startsWith("[")) {
        Integer closing = host.indexOf("]")
        if (closing < 0) return false
        String suffix = host.substring(closing + 1)
        if (!validHostPort(suffix)) return false
        host = host.substring(1, closing)
    } else if (host.count(":") == 1) {
        String suffix = host.substring(host.indexOf(":"))
        if (!validHostPort(suffix)) return false
        host = host.substring(0, host.indexOf(":"))
    } else if (host.count(":") > 1) {
        return false
    }
    return isPrivateAddress(host)
}

private boolean validHostPort(String suffix) {
    if (!suffix) return true
    if (!(suffix ==~ /^:[0-9]{1,5}$/)) return false
    Integer port = suffix.substring(1) as Integer
    return port >= 1 && port <= 65535
}

private boolean isPrivateAddress(String value) {
    if (!value) return false
    String address = value.trim().toLowerCase()
    if (address.contains(":")) return isPrivateIpv6Literal(address)
    List<String> parts = address.split(/\./, -1) as List<String>
    if (parts.size() != 4 || !parts.every { String item ->
        item ==~ /^[0-9]{1,3}$/ && (item == "0" || !item.startsWith("0"))
    }) return false
    List<Integer> octets = parts.collect { String item -> item as Integer }
    if (octets.any { Integer item -> item > 255 }) return false
    return octets[0] == 10 || octets[0] == 127 ||
        (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31) ||
        (octets[0] == 192 && octets[1] == 168) ||
        (octets[0] == 169 && octets[1] == 254)
}

private boolean isPrivateIpv6Literal(String address) {
    if (!address || !(address ==~ /^[0-9a-f:]+$/) || address.count("::") > 1) return false
    List<String> groups = []
    if (address.contains("::")) {
        List<String> sides = address.split(/::/, -1) as List<String>
        if (sides.size() != 2) return false
        List<String> left = sides[0] ? (sides[0].split(/:/, -1) as List<String>) : []
        List<String> right = sides[1] ? (sides[1].split(/:/, -1) as List<String>) : []
        Integer missing = 8 - left.size() - right.size()
        if (missing < 1) return false
        groups.addAll(left)
        missing.times { groups.add("0") }
        groups.addAll(right)
    } else {
        groups = address.split(/:/, -1) as List<String>
        if (groups.size() != 8) return false
    }
    if (groups.size() != 8 || !groups.every { String group -> group ==~ /^[0-9a-f]{1,4}$/ }) {
        return false
    }
    List<Integer> hextets = groups.collect { String group -> Integer.parseInt(group, 16) }
    boolean loopback = hextets.take(7).every { Integer group -> group == 0 } && hextets[7] == 1
    Integer first = hextets[0]
    return loopback || (first & 0xfe00) == 0xfc00 || (first & 0xffc0) == 0xfe80
}

private boolean hasDuplicateObjectKeys(String raw) {
    List<Set<String>> scopes = []
    Integer index = 0
    while (index < raw.length()) {
        Character current = raw.charAt(index)
        if (current == '{'.charAt(0)) {
            scopes.add([] as Set<String>)
            index += 1
            continue
        }
        if (current == '}'.charAt(0)) {
            if (!scopes.isEmpty()) scopes.remove(scopes.size() - 1)
            index += 1
            continue
        }
        if (current != '"'.charAt(0)) {
            index += 1
            continue
        }
        Integer start = index
        index += 1
        boolean escaped = false
        while (index < raw.length()) {
            Character item = raw.charAt(index)
            if (escaped) {
                escaped = false
            } else if (item == '\\'.charAt(0)) {
                escaped = true
            } else if (item == '"'.charAt(0)) {
                break
            }
            index += 1
        }
        if (index >= raw.length()) return false
        Integer end = index
        Integer next = end + 1
        while (next < raw.length() && Character.isWhitespace(raw.charAt(next))) next += 1
        if (next < raw.length() && raw.charAt(next) == ':'.charAt(0) && !scopes.isEmpty()) {
            String key = new JsonSlurper().parseText(raw.substring(start, end + 1)) as String
            Set<String> keys = scopes[scopes.size() - 1]
            if (keys.contains(key)) return true
            keys.add(key)
        }
        index = end + 1
    }
    return false
}

private Map validateRequest(Map body) {
    return validateRequestBody(body, false)
}

private Map validateRequestBody(Map body, Boolean allowExpired) {
    if (containsUnsafeContent(body)) return rejected(422, "unsafe_content")
    if (body.keySet() != REQUEST_KEYS || body.contract_version != CONTRACT_VERSION) {
        return rejected(400, "closed_schema")
    }
    Map registration = activeProjectionRegistry()[body.projection_id as String]
    if (!registration) return rejected(404, "unregistered_projection")
    if (!(body.origin instanceof Map) || (body.origin as Map).keySet() != ORIGIN_KEYS) {
        return rejected(400, "invalid_origin")
    }
    Map origin = body.origin as Map
    if (![body.projection_id, body.idempotency_id, body.evidence_ref, origin.representation_id,
        origin.observation_id].every { Object item -> validTypedId(item) } ||
        !((origin.ecosystem as String) ==~ /^[a-z][a-z0-9._-]{0,63}$/)) {
        return rejected(400, "invalid_identity")
    }
    if (!((registration.sourceEcosystems as Set).contains(origin.ecosystem))) {
        return rejected(422, "source_mismatch")
    }
    if (!exactScalarType(body.value, registration.valueType as String) || body.unit != registration.unit) {
        return rejected(422, "type_or_unit_mismatch")
    }
    if (!validSequence(body.sequence) || !(body.freshness in FRESHNESS_VALUES) ||
        !(body.availability in AVAILABILITY_VALUES) || !(body.confidence in CONFIDENCE_VALUES) ||
        !(body.contradiction_status in CONTRADICTION_VALUES) ||
        !(body.uncertainty == null || SAFE_UNCERTAINTY_VALUES.contains(body.uncertainty))) {
        return rejected(422, "invalid_meaning")
    }
    Date expiry = parseTimestamp(body.expires_at)
    Date derived = parseTimestamp(body.derived_at)
    Date observed = parseTimestamp(body.observed_at)
    Date currentTime = new Date()
    if (!expiry || !derived || !observed || observed.after(derived) || derived.after(expiry) ||
        derived.after(currentTime) || (!allowExpired && !expiry.after(currentTime))) {
        return rejected(422, "expired_or_invalid_time")
    }
    boolean actionable = body.freshness in ["current", "aging"] && body.availability == "available" &&
        body.confidence != "unknown" && body.contradiction_status in ["none", "resolved"]
    return [accepted: true, actionable: actionable, status: 200]
}

private Map validateOrdering(Map body) {
    List<Map> recent = boundedHistory(retainedAuthority())
    Map replay = recent.find { Map retained -> retained.idempotency_id == body.idempotency_id }
    if (replay) {
        if ((replay.request as Map) != body) return rejected(409, "idempotency_conflict")
        return [accepted: true, replay: true, received_at: replay.received_at]
    }
    List<Map> projectionHistory = recent.findAll { Map retained ->
        retained.projection_id == body.projection_id
    }
    Map sequenceReplay = projectionHistory.find { Map retained ->
        (retained.sequence as BigDecimal) == (body.sequence as BigDecimal)
    }
    if (sequenceReplay) return rejected(409, "sequence_conflict")
    if (!projectionHistory.isEmpty()) {
        BigDecimal prior = projectionHistory.collect { Map retained -> retained.sequence as BigDecimal }.max() as BigDecimal
        if ((body.sequence as BigDecimal) < prior) return rejected(409, "out_of_order")
    }
    return [accepted: true, replay: false]
}

private Map retainedAuthority() {
    if (atomicState.bridgeState instanceof Map) {
        Map authority = atomicState.bridgeState as Map
        if (authority.version == 2) return sanitizeUnifiedAuthority(authority)
        return reconcileLegacyAuthority(
            authority.recent instanceof List ? (authority.recent as List) : [],
            authority.projections instanceof Map ? (authority.projections as Map) : [:]
        )
    }
    Map projections = atomicState.projections instanceof Map ? (atomicState.projections as Map) : [:]
    List recent = atomicState.recentPublicationHistory instanceof List ?
        (atomicState.recentPublicationHistory as List) : []
    return reconcileLegacyAuthority(recent, projections)
}

private Map reconcileLegacyAuthority(List rawRecent, Map rawProjections) {
    List<Map> recent = []
    Map projections = [:]
    Set<String> invalidProjectionIds = [] as Set<String>
    Integer dropped = 0
    rawProjections.each { Object projectionKey, Object candidate ->
        List matchingRows = rawRecent.findAll { Object retained ->
            retained instanceof Map && candidate instanceof Map && (retained as Map) == (candidate as Map)
        }
        Map matching = matchingRows.size() == 1 ? (matchingRows[0] as Map) : null
        if (matching && validRetainedRow(matching, projectionKey)) {
            Map safe = copyRetainedRow(matching)
            recent.add(safe)
            projections[projectionKey as String] = safe
        } else {
            dropped += 1
            rememberRegisteredProjection(invalidProjectionIds, projectionKey)
            if (candidate instanceof Map) {
                rememberRegisteredProjection(invalidProjectionIds, (candidate as Map).projection_id)
            }
        }
    }
    rawRecent.each { Object retained ->
        boolean matched = recent.any { Map safe -> retained instanceof Map && safe == (retained as Map) }
        if (!matched) {
            dropped += 1
            if (retained instanceof Map) {
                rememberRegisteredProjection(invalidProjectionIds, (retained as Map).projection_id)
            }
        }
    }
    return [version: 2, recent: recent.takeRight(MAX_RECENT_IDEMPOTENCY), projections: projections,
        quarantined_count: dropped, invalid_projection_ids: invalidProjectionIds as List]
}

private Map sanitizeUnifiedAuthority(Map rawAuthority) {
    List rawRecent = rawAuthority.recent instanceof List ? (rawAuthority.recent as List) : []
    Map rawProjections = rawAuthority.projections instanceof Map ? (rawAuthority.projections as Map) : [:]
    Map sanitized = reconcileLegacyAuthority(rawRecent, rawProjections)
    Integer priorDropped = rawAuthority.quarantined_count instanceof Number ?
        (rawAuthority.quarantined_count as Integer) : 0
    sanitized.quarantined_count = priorDropped + (sanitized.quarantined_count as Integer)
    return sanitized
}

private boolean validRetainedRow(Map retained, Object projectionKey) {
    if (retained.keySet() != RETAINED_KEYS || !(retained.request instanceof Map)) return false
    Map body = retained.request as Map
    Map verdict = validateRequestBody(body, true)
    if (!verdict.accepted || projectionKey != body.projection_id || retained.projection_id != body.projection_id ||
        retained.idempotency_id != body.idempotency_id || !validSequence(retained.sequence) ||
        (retained.sequence as BigDecimal) != (body.sequence as BigDecimal)) return false
    Date received = parseTimestamp(retained.received_at)
    Date derived = parseTimestamp(body.derived_at)
    Date expiry = parseTimestamp(body.expires_at)
    return received && derived && expiry && !received.before(derived) && received.before(expiry)
}

private Map copyRetainedRow(Map retained) {
    return [request: new LinkedHashMap(retained.request as Map), sequence: retained.sequence,
        idempotency_id: retained.idempotency_id, projection_id: retained.projection_id,
        received_at: retained.received_at]
}

private void rememberRegisteredProjection(Set<String> projectionIds, Object candidate) {
    if (candidate instanceof String && PROJECTION_REGISTRY.containsKey(candidate as String)) {
        projectionIds.add(candidate as String)
    }
}

private void invalidateQuarantinedProjections(Map authority) {
    List invalid = authority.invalid_projection_ids instanceof List ?
        (authority.invalid_projection_ids as List) : []
    invalid.each { Object projectionId ->
        if (projectionId instanceof String && PROJECTION_REGISTRY.containsKey(projectionId as String)) {
            def projection = getChildDevice(PROJECTION_REGISTRY[projectionId as String].dni as String)
            if (projection) failClosedActionable(projectionId as String, projection)
        }
    }
}

private void migrateRetainedAuthority() {
    Map authority = retainedAuthority()
    invalidateQuarantinedProjections(authority)
    atomicState.bridgeState = [version: 2, recent: authority.recent, projections: authority.projections,
        quarantined_count: authority.quarantined_count ?: 0]
    atomicState.remove("projections")
    atomicState.remove("recentPublicationHistory")
}

private List<Map> boundedHistory(Map authority) {
    List<Map> recent = authority.recent instanceof List ? (authority.recent as List<Map>) : []
    return recent.takeRight(MAX_RECENT_IDEMPOTENCY)
}

private boolean commitRetainedObservation(Map body, String receivedAt) {
    try {
        Map authority = retainedAuthority()
        Map projections = new LinkedHashMap((authority.projections ?: [:]) as Map)
        List<Map> recent = boundedHistory(authority).collect { Map item -> new LinkedHashMap(item) }
        Map retained = [request: new LinkedHashMap(body), sequence: body.sequence,
            idempotency_id: body.idempotency_id, projection_id: body.projection_id, received_at: receivedAt]
        recent.removeAll { Map item -> item.projection_id == body.projection_id }
        recent.add(retained)
        projections[body.projection_id as String] = retained
        atomicState.bridgeState = [version: 2, recent: recent.takeRight(MAX_RECENT_IDEMPOTENCY),
            projections: projections, quarantined_count: authority.quarantined_count ?: 0]
        return true
    } catch (ignored) {
        return false
    }
}

private boolean validSequence(Object value) {
    if (!(value instanceof Number)) return false
    if (value instanceof Double && !Double.isFinite(value as Double)) return false
    if (value instanceof Float && !Float.isFinite(value as Float)) return false
    try {
        BigDecimal sequence = new BigDecimal(value.toString())
        return sequence.stripTrailingZeros().scale() <= 0 && sequence >= BigDecimal.ZERO && sequence <= MAX_SEQUENCE
    } catch (NumberFormatException ignored) {
        return false
    }
}

private boolean validTypedId(Object value) {
    return value instanceof String && TYPED_ID.matcher(value as String).matches()
}

private boolean exactScalarType(Object value, String expected) {
    if (expected == "boolean") return value instanceof Boolean
    if (expected == "number") {
        boolean supportedNumber = value instanceof Integer || value instanceof Long ||
            value instanceof BigInteger || value instanceof BigDecimal ||
            value instanceof Double || value instanceof Float
        if (!supportedNumber) return false
        if (value instanceof Double && !Double.isFinite(value as Double)) return false
        if (value instanceof Float && !Float.isFinite(value as Float)) return false
        return new BigDecimal(value.toString()).abs() <= MAX_NUMBER
    }
    if (expected == "text") return value instanceof String &&
        (value as String).size() >= 1 && (value as String).size() <= MAX_TEXT_LENGTH
    return false
}

private boolean containsUnsafeContent(Map body) {
    List<Object> pending = [body]
    Integer inspected = 0
    while (!pending.isEmpty()) {
        Object current = pending.remove(pending.size() - 1)
        inspected += 1
        if (inspected > MAX_CONTENT_NODES) return true
        if (current instanceof Map) {
            (current as Map).each { Object key, Object value ->
                pending.add(key)
                pending.add(value)
            }
        } else if (current instanceof Collection) {
            return true
        } else if (current instanceof String) {
            String text = current as String
            if (UNSAFE_TEXT.matcher(text).find() || ENCODED_TEXT.matcher(text).find()) return true
        } else if (current != null && !(current instanceof Number) && !(current instanceof Boolean)) {
            return true
        }
    }
    return false
}

private Date parseTimestamp(Object value) {
    if (!(value instanceof String)) return null
    try {
        return Date.from(java.time.OffsetDateTime.parse(value as String).toInstant())
    } catch (ignored) {
        return null
    }
}

private String fixedAttribute(String projectionId, String meaning) {
    return ((PROJECTION_REGISTRY[projectionId].attributes as Map)[meaning]) as String
}

private boolean publishObservation(
    String projectionId, projection, Map body, String receivedAt, Boolean actionable
) {
    Map registration = PROJECTION_REGISTRY[projectionId] as Map
    Map observation = new LinkedHashMap(body)
    if (!actionable) {
        observation.freshness = body.freshness in ["stale", "expired"] ? body.freshness : "unknown"
        observation.availability = "unknown"
        observation.confidence = "unknown"
    }
    Map origin = observation.origin as Map
    String health = observation.contradiction_status == "open" ? "contradictory" :
        (observation.availability == "available" ? "available" : "unknown")
    try {
        projection.sendEvent(name: fixedAttribute(projectionId, "actionable"), value: "false")
        projection.sendEvent(name: fixedAttribute(projectionId, "kind"), value: "hubitat.projected-observation")
        projection.sendEvent(name: fixedAttribute(projectionId, "schemaVersion"), value: observation.contract_version)
        projection.sendEvent(name: fixedAttribute(projectionId, "recordId"), value: observation.idempotency_id)
        projection.sendEvent(name: fixedAttribute(projectionId, "nativeId"), value: registration.dni)
        projection.sendEvent(name: fixedAttribute(projectionId, "nativeExtensions"), value: "{}")
        projection.sendEvent(name: fixedAttribute(projectionId, "projectionId"), value: observation.projection_id)
        projection.sendEvent(name: fixedAttribute(projectionId, "idempotencyId"), value: observation.idempotency_id)
        projection.sendEvent(name: fixedAttribute(projectionId, "originEcosystem"), value: origin.ecosystem)
        projection.sendEvent(name: fixedAttribute(projectionId, "originRepresentationId"),
            value: origin.representation_id)
        projection.sendEvent(name: fixedAttribute(projectionId, "originObservationId"),
            value: origin.observation_id)
        projection.sendEvent(name: fixedAttribute(projectionId, "value"), value: observation.value,
            unit: observation.unit)
        projection.sendEvent(name: fixedAttribute(projectionId, "freshness"), value: observation.freshness)
        projection.sendEvent(name: fixedAttribute(projectionId, "availability"), value: observation.availability)
        projection.sendEvent(name: fixedAttribute(projectionId, "confidence"), value: observation.confidence)
        projection.sendEvent(name: fixedAttribute(projectionId, "provenance"), value: observation.evidence_ref)
        projection.sendEvent(name: fixedAttribute(projectionId, "provenanceRefs"), value: observation.evidence_ref)
        projection.sendEvent(name: fixedAttribute(projectionId, "coverageRef"), value: COVERAGE_REF)
        projection.sendEvent(name: fixedAttribute(projectionId, "uncertainty"),
            value: observation.uncertainty ?: "none")
        projection.sendEvent(name: fixedAttribute(projectionId, "contradiction"),
            value: observation.contradiction_status)
        projection.sendEvent(name: fixedAttribute(projectionId, "observedAt"), value: observation.observed_at)
        projection.sendEvent(name: fixedAttribute(projectionId, "sourceEventAt"), value: observation.observed_at)
        projection.sendEvent(name: fixedAttribute(projectionId, "derivedAt"), value: observation.derived_at)
        projection.sendEvent(name: fixedAttribute(projectionId, "receivedAt"), value: receivedAt)
        projection.sendEvent(name: fixedAttribute(projectionId, "expiresAt"), value: observation.expires_at)
        projection.sendEvent(name: fixedAttribute(projectionId, "sequence"), value: observation.sequence)
        projection.sendEvent(name: fixedAttribute(projectionId, "unit"), value: observation.unit ?: "none")
        projection.sendEvent(name: fixedAttribute(projectionId, "registrationPolicyId"),
            value: registration.policyId)
        projection.sendEvent(name: fixedAttribute(projectionId, "registrationPolicyVersion"),
            value: registration.policyVersion)
        projection.sendEvent(name: fixedAttribute(projectionId, "health"), value: health)
        projection.sendEvent(name: fixedAttribute(projectionId, "independence"), value: "same-origin")
        if (actionable) {
            projection.sendEvent(name: fixedAttribute(projectionId, "actionable"), value: "true")
        }
        return true
    } catch (ignored) {
        failClosedActionable(projectionId, projection)
        return false
    }
}

private void failClosedActionable(String projectionId, projection) {
    try {
        projection.sendEvent(name: fixedAttribute(projectionId, "actionable"), value: "false")
    } catch (ignored) {
        // The categorical HTTP rejection remains content-free even if the child is unavailable.
    }
}

def expireObservations() {
    invalidateInactiveProjectionChildren(activeProjectionRegistry())
    Map authority = retainedAuthority()
    invalidateQuarantinedProjections(authority)
    Map projections = authority.projections as Map
    Date now = new Date()
    projections.each { String projectionId, Map retained ->
        Map body = retained.request as Map
        Date expiry = parseTimestamp(body.expires_at)
        if (expiry && !expiry.after(now) && PROJECTION_REGISTRY.containsKey(projectionId)) {
            def projection = getChildDevice(PROJECTION_REGISTRY[projectionId].dni as String)
            if (projection) {
                Map expired = body + [freshness: "expired", availability: "unknown", confidence: "unknown",
                    contradiction_status: body.contradiction_status ?: "unknown"]
                publishObservation(projectionId, projection, expired, now.toInstant().toString(), false)
            }
        }
    }
}

private Map rejected(Integer status, String reason) {
    return [accepted: false, status: status, reason: reason]
}

private def renderRejection(Integer status, String reason) {
    return render(status: status, contentType: "application/json",
        data: JsonOutput.toJson([outcome: "rejected", reason: reason]))
}

private def renderReceipt(Map body, String receivedAt) {
    return render(status: 200, contentType: "application/json", data: JsonOutput.toJson([
        contract_version: CONTRACT_VERSION,
        outcome: "accepted",
        projection_id: body.projection_id,
        idempotency_id: body.idempotency_id,
        sequence: body.sequence,
        received_at: receivedAt
    ]))
}
