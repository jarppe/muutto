-- requires: init.sql

create table foo (
  id    serial not null primary key,
  value text   not null
);
