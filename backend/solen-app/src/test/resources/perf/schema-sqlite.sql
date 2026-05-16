create table if not exists device (
    device_id varchar(32) not null primary key,
    status varchar(32) not null,
    lac bigint not null,
    ci bigint not null,
    input_stat integer not null,
    output_stat integer not null,
    rssi integer not null,
    voltage double not null,
    temperature double not null,
    gravity integer not null,
    uptime integer not null,
    lat double not null,
    lng double not null
);

create table if not exists connection (
    connection_id varchar(64) not null primary key,
    device_id varchar(32) not null
);

create index if not exists idx_connection_device_id on connection (device_id);

create table if not exists event (
    event_id integer not null primary key,
    device_id varchar(32) not null,
    type varchar(32) not null,
    time timestamp not null,
    details varchar(1024) not null
);

create index if not exists idx_event_device_id on event (device_id);
create index if not exists idx_event_type on event (type);
create index if not exists idx_event_time on event (time);
create index if not exists idx_event_device_id_event_id on event (device_id, event_id desc);
