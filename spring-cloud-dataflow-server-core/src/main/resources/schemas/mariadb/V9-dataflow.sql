CREATE VIEW AGGREGATE_TASK_EXECUTION AS
(SELECT TASK_EXECUTION_ID, START_TIME, END_TIME, TASK_NAME, EXIT_CODE, EXIT_MESSAGE, ERROR_MESSAGE, LAST_UPDATED, EXTERNAL_EXECUTION_ID, PARENT_EXECUTION_ID, 'boot2' AS SCHEMA_TARGET FROM TASK_EXECUTION)
UNION ALL
(SELECT TASK_EXECUTION_ID, START_TIME, END_TIME, TASK_NAME, EXIT_CODE, EXIT_MESSAGE, ERROR_MESSAGE, LAST_UPDATED, EXTERNAL_EXECUTION_ID, PARENT_EXECUTION_ID, 'boot3' AS SCHEMA_TARGET FROM BOOT3_TASK_EXECUTION)
ORDER BY TASK_EXECUTION_ID DESC;

CREATE VIEW AGGREGATE_TASK_EXECUTION_PARAMS AS
SELECT TASK_EXECUTION_ID, TASK_PARAM, 'boot2' AS SCHEMA_TARGET FROM TASK_EXECUTION_PARAMS
UNION ALL
SELECT TASK_EXECUTION_ID, TASK_PARAM, 'boot3' AS SCHEMA_TARGET FROM BOOT3_TASK_EXECUTION_PARAMS;

CREATE VIEW AGGREGATE_JOB_EXECUTION AS
(SELECT JOB_EXECUTION_ID, VERSION, JOB_INSTANCE_ID, CREATE_TIME, START_TIME, END_TIME, STATUS, EXIT_CODE, EXIT_MESSAGE, LAST_UPDATED, 'boot2' AS SCHEMA_TARGET FROM BATCH_JOB_EXECUTION)
UNION ALL
(SELECT JOB_EXECUTION_ID, VERSION, JOB_INSTANCE_ID, CREATE_TIME, START_TIME, END_TIME, STATUS, EXIT_CODE, EXIT_MESSAGE, LAST_UPDATED, 'boot3' AS SCHEMA_TARGET FROM BOOT3_BATCH_JOB_EXECUTION)
ORDER BY JOB_EXECUTION_ID DESC;

CREATE VIEW AGGREGATE_JOB_INSTANCE AS
(SELECT JOB_INSTANCE_ID, VERSION, JOB_NAME, JOB_KEY, 'boot2' AS SCHEMA_TARGET FROM BATCH_JOB_INSTANCE)
UNION ALL
(SELECT JOB_INSTANCE_ID, VERSION, JOB_NAME, JOB_KEY, 'boot3' AS SCHEMA_TARGET FROM BOOT3_BATCH_JOB_INSTANCE)
ORDER BY JOB_INSTANCE_ID DESC;

CREATE VIEW AGGREGATE_TASK_BATCH AS
(SELECT TASK_EXECUTION_ID, JOB_EXECUTION_ID, 'boot2' AS SCHEMA_TARGET FROM TASK_TASK_BATCH)
UNION ALL
(SELECT TASK_EXECUTION_ID, JOB_EXECUTION_ID, 'boot3' AS SCHEMA_TARGET FROM BOOT3_TASK_TASK_BATCH)
ORDER BY TASK_EXECUTION_ID DESC;

CREATE VIEW AGGREGATE_STEP_EXECUTION AS
(SELECT STEP_EXECUTION_ID, VERSION, STEP_NAME, JOB_EXECUTION_ID, START_TIME, END_TIME, STATUS, COMMIT_COUNT, READ_COUNT, FILTER_COUNT, WRITE_COUNT, READ_SKIP_COUNT, WRITE_SKIP_COUNT, PROCESS_SKIP_COUNT, ROLLBACK_COUNT, EXIT_CODE, EXIT_MESSAGE, LAST_UPDATED, 'boot2' AS SCHEMA_TARGET FROM BATCH_STEP_EXECUTION)
UNION ALL
(SELECT STEP_EXECUTION_ID, VERSION, STEP_NAME, JOB_EXECUTION_ID, START_TIME, END_TIME, STATUS, COMMIT_COUNT, READ_COUNT, FILTER_COUNT, WRITE_COUNT, READ_SKIP_COUNT, WRITE_SKIP_COUNT, PROCESS_SKIP_COUNT, ROLLBACK_COUNT, EXIT_CODE, EXIT_MESSAGE, LAST_UPDATED, 'boot3' AS SCHEMA_TARGET FROM BOOT3_BATCH_STEP_EXECUTION)
ORDER BY STEP_EXECUTION_ID DESC;