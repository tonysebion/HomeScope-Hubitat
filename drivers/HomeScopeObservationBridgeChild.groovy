/**
 * Fixed, command-free child representation for HomeScope advisory observations.
 *
 * The parent app alone emits these attributes. The driver deliberately declares
 * no command, actuator, configuration, or physical-device capability.
 */
metadata {
    definition(
        name: "HomeScope Observation Bridge Child",
        namespace: "homescope",
        author: "HomeScope"
    ) {
        capability "Sensor"

        attribute "observationKind", "STRING"
        attribute "observationSchemaVersion", "STRING"
        attribute "observationRecordId", "STRING"
        attribute "observationNativeId", "STRING"
        attribute "observationNativeExtensions", "STRING"
        attribute "observationProjectionId", "STRING"
        attribute "observationIdempotencyId", "STRING"
        attribute "observationOriginEcosystem", "STRING"
        attribute "observationOriginRepresentationId", "STRING"
        attribute "observationOriginObservationId", "STRING"
        attribute "observationValue", "ENUM", ["true", "false"]
        attribute "observationFreshness", "ENUM",
            ["current", "aging", "stale", "expired", "unknown"]
        attribute "observationAvailability", "ENUM",
            ["available", "unavailable", "unknown"]
        attribute "observationConfidence", "ENUM",
            ["confirmed", "high", "medium", "low", "unknown"]
        attribute "observationProvenance", "STRING"
        attribute "observationProvenanceRefs", "STRING"
        attribute "observationCoverageRef", "STRING"
        attribute "observationUncertainty", "STRING"
        attribute "observationContradiction", "ENUM",
            ["none", "resolved", "open", "unknown"]
        attribute "observationObservedAt", "STRING"
        attribute "observationSourceEventAt", "STRING"
        attribute "observationDerivedAt", "STRING"
        attribute "observationReceivedAt", "STRING"
        attribute "observationExpiresAt", "STRING"
        attribute "observationSequence", "NUMBER"
        attribute "observationUnit", "STRING"
        attribute "observationRegistrationPolicyId", "STRING"
        attribute "observationRegistrationPolicyVersion", "STRING"
        attribute "observationHealth", "ENUM",
            ["available", "unavailable", "contradictory", "unknown"]
        attribute "observationIndependence", "ENUM", ["same-origin"]
        attribute "observationActionable", "ENUM", ["true", "false"]
    }
}
