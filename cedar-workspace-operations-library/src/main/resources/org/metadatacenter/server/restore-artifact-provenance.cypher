// Prepared for an explicitly approved restoration after fresh document/graph preflight.
// Run with application writes paused. $rows is a reviewed batch of at most 100 unique IDs.
// The lifecycle lock must already exist; this query does not create a competing lock.
MATCH (lock:CedarVersionLock {id:'lifecycle'})
SET lock.revision = coalesce(lock.revision, 0) + 1
WITH lock
WHERE size($rows) > 0 AND size($rows) <= 100
UNWIND $rows AS row
MATCH (artifact:Artifact {_id:row.id})
WHERE artifact.oslc_modifiedBy = row.expectedModifiedBy
  AND artifact.lastUpdatedOnTS = row.expectedLastUpdatedOnTS
  AND coalesce(artifact._cedarRevision, 1) = row.expectedGraphRevision
  AND NOT EXISTS { MATCH (pending:CedarVersionProjection {resourceId:row.id}) }
  AND NOT EXISTS { MATCH (pending:CedarArtifactRestoreOutbox {resourceId:row.id}) }
  AND NOT EXISTS { MATCH (pending:CedarArtifactDeletionOutbox {resourceId:row.id}) }
WITH collect({artifact:artifact, row:row}) AS matches, count(DISTINCT artifact) AS uniqueCount
WHERE size(matches) = size($rows) AND uniqueCount = size($rows)
UNWIND matches AS item
WITH item.artifact AS artifact, item.row AS row
SET artifact.oslc_modifiedBy = row.modifiedBy,
    artifact.pav_lastUpdatedOn = row.lastUpdatedOn,
    artifact.lastUpdatedOnTS = row.lastUpdatedOnTS,
    artifact._cedarRevision = coalesce(artifact._cedarRevision, 1) + 1
MERGE (projection:CedarVersionProjection {resourceId:row.id})
SET projection.syncPrevious = false, projection.updatedAt = timestamp()
RETURN artifact._id AS id, artifact.oslc_modifiedBy AS modifiedBy,
       artifact.pav_lastUpdatedOn AS lastUpdatedOn, artifact.lastUpdatedOnTS AS lastUpdatedOnTS
