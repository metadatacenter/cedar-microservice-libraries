-- Demo seed for the aggregation rollups so the "Logs & Usage" page shows realistic data locally,
-- WITHOUT running the aggregator against real traffic. Run against the local dbLogging DB (cedar_log).
-- Safe to re-run: it deletes its own sample rows first (recent hours). Column names are camelCase to
-- match Hibernate. Histograms are shape-only (percentiles derive from them; counts come from reqCount).
--   mysql -u cedarMySQLLogUser -p cedar_log < sample-agg-data.sql
-- With hibernate.jdbc.time_zone=UTC on the log DB, hourUtc is true UTC, so seed in UTC.
SET @h0 = DATE_FORMAT(UTC_TIMESTAMP() - INTERVAL 2 HOUR, '%Y-%m-%d %H:00:00');
SET @h1 = DATE_FORMAT(UTC_TIMESTAMP() - INTERVAL 3 HOUR, '%Y-%m-%d %H:00:00');

DELETE FROM agg_request_hourly      WHERE hourUtc >= NOW() - INTERVAL 2 DAY;
DELETE FROM agg_request_user_hourly WHERE hourUtc >= NOW() - INTERVAL 2 DAY;
DELETE FROM agg_cypher_hourly       WHERE hourUtc >= NOW() - INTERVAL 2 DAY;

-- endpoints (component, class, method, httpMethod, statusClass, authSource, req, err, sum/min/max, h0..h14, samplePath)
INSERT INTO agg_request_hourly
 (hourUtc, systemComponentName, className, methodName, httpMethod, statusClass, authSource, reqCount, errorCount,
  sumHandlerNanos, minHandlerNanos, maxHandlerNanos, sumPreHandlerNanos,
  h0,h1,h2,h3,h4,h5,h6,h7,h8,h9,h10,h11,h12,h13,h14, samplePath) VALUES
 (@h0,'resource','FolderContentsResource','findFolderContents','GET','2xx','token', 4200, 12, 88000000000, 900000, 240000000, 6000000000, 0,0,40,300,1400,1600,700,120,30,8,2,0,0,0,0,'/folders/{id}/contents'),
 (@h0,'resource','SearchResource','search','GET','2xx','token', 2600, 41, 130000000000, 2000000, 900000000, 4000000000, 0,0,0,60,400,900,800,300,90,40,10,0,0,0,0,'/search'),
 (@h0,'terminology','BioPortalSearchResource','search','GET','2xx','apiKey', 1800, 63, 360000000000, 8000000, 3200000000, 2200000000, 0,0,0,0,40,180,520,600,300,110,40,10,0,0,0,'/bioportal/search'),
 (@h0,'artifact','TemplateInstancesResource','createInstance','POST','2xx','token', 900, 9, 84000000000, 5000000, 700000000, 1200000000, 0,0,0,20,120,300,280,120,40,15,5,0,0,0,0,'/template-instances'),
 (@h0,'submission','SubmissionResource','submit','POST','5xx','apiKey', 140, 22, 60000000000, 40000000, 6000000000, 900000000, 0,0,0,0,0,4,20,30,35,25,15,7,3,1,0,'/command/submit'),
 (@h1,'resource','FolderContentsResource','findFolderContents','GET','2xx','token', 3800, 10, 79000000000, 900000, 210000000, 5400000000, 0,0,36,280,1300,1500,600,60,18,6,0,0,0,0,0,'/folders/{id}/contents'),
 (@h1,'user','UsersResource','listUsers','GET','2xx','token', 320, 1, 6400000000, 700000, 60000000, 400000000, 0,20,120,110,50,15,5,0,0,0,0,0,0,0,0,'/users'),
 (@h1,'schema','ValidationResource','validate','POST','4xx','apiKey', 260, 48, 12000000000, 1000000, 200000000, 300000000, 0,0,10,60,90,60,25,10,5,0,0,0,0,0,0,'/validate');

-- cypher shapes
INSERT INTO agg_cypher_hourly
 (hourUtc, systemComponentName, operation, runnableHash, execCount, sumNanos, minNanos, maxNanos,
  h0,h1,h2,h3,h4,h5,h6,h7,h8,h9,h10,h11,h12,h13,h14) VALUES
 (@h0,'resource','MATCH_read','7be220a1c9f04d2e', 5400, 62000000000, 800000, 90000000, 0,60,900,1800,1500,700,300,120,20,0,0,0,0,0,0),
 (@h0,'resource','PERMISSION','f0aa9e33b1774c05', 220, 84000000000, 20000000, 900000000, 0,0,0,0,4,20,40,60,50,30,12,4,0,0,0),
 (@h0,'artifact','MERGE_upsert','c810447725ab9f13', 640, 40000000000, 5000000, 300000000, 0,0,0,40,120,200,160,80,30,10,0,0,0,0,0),
 (@h1,'terminology','MATCH_read','9c5e0388ad4471ff', 3100, 44000000000, 900000, 120000000, 0,20,400,900,900,600,220,50,10,0,0,0,0,0,0),
 (@h1,'submission','CREATE_write','22d7b1cc55e01a94', 90, 11000000000, 8000000, 400000000, 0,0,0,0,10,20,25,20,10,4,1,0,0,0,0);

INSERT INTO agg_cypher_query_catalog (runnableHash, operation, runnableSample, interpolatedSample, sampleClassName, sampleMethodName, firstSeen, lastSeen) VALUES
 ('7be220a1c9f04d2e','MATCH_read','MATCH (u:<User> {id:{userId}})-[:<CAN_READ>]->(n) RETURN n LIMIT {limit}', NULL,'Neo4JProxyResource','findReadable', @h1, @h0),
 ('f0aa9e33b1774c05','PERMISSION','MATCH (u)-[r:<CAN_READ|CAN_WRITE>*1..6]->(n:<Node>) WHERE u.id={userId} RETURN r', NULL,'Neo4JProxyPermission','materialize', @h1, @h0),
 ('c810447725ab9f13','MERGE_upsert','MERGE (t:<TemplateInstance> {id:{id}}) SET t += {props} RETURN t', NULL,'Neo4JProxyArtifact','upsert', @h1, @h0),
 ('9c5e0388ad4471ff','MATCH_read','MATCH (c:<Concept>)-[:<BROADER>]->(p) WHERE c.iri={iri} RETURN p', NULL,'Neo4JProxyTerminology','broader', @h1, @h0),
 ('22d7b1cc55e01a94','CREATE_write','CREATE (s:<Submission> {id:{id}, status:{status}}) RETURN s', NULL,'Neo4JProxySubmission','create', @h1, @h0)
ON DUPLICATE KEY UPDATE lastSeen = VALUES(lastSeen);

-- users & API keys (heavy pipeline keys, a couple of humans, one idle-ish)
INSERT INTO agg_request_user_hourly (hourUtc, userId, authSource, apiKeyHash, reqCount, errorCount, sumHandlerNanos) VALUES
 (@h0,'https://metadatacenter.org/users/pipeline-ingest','apiKey','a1b2c3d4e5f60718', 5200, 21, 160000000000),
 (@h0,'https://metadatacenter.org/users/immport-sync','apiKey','1122334455667788', 3100, 66, 210000000000),
 (@h0,'https://metadatacenter.org/users/a.egyed','token','', 640, 4, 18000000000),
 (@h0,'https://metadatacenter.org/users/m.martin','token','', 410, 2, 9000000000),
 (@h1,'https://metadatacenter.org/users/bioportal-mirror','apiKey','99aa88bb77cc66dd', 1750, 16, 62000000000),
 (@h1,'https://metadatacenter.org/users/datacite-bot','apiKey','deadbeefcafe0011', 12, 0, 240000000);
