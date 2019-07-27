    create table AUDIT_RECORDS (
       id bigint not null,
        audit_Action bigint,
        audit_data long varchar,
        audit_Operation bigint,
        correlation_id varchar(255),
        created_by varchar(255),
        created_On timestamp,
        primary key (id)
    );

    alter table stream_definitions add description varchar(255)
    alter table task_definitions add description varchar(255)
