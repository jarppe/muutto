--
-- Initialize db for testing:
--

-- Create schema 'pgunit' for PGunit library. Allow example_app and example_use to use
-- it. This is not necessary for the application, but it makes testing easier when the test
-- can switch to example_app role and still call pgunit functions.

create schema if not exists pgunit;
create extension if not exists dblink schema pgunit;
grant usage on schema pgunit to example_app, example_user;
alter default privileges in schema pgunit grant execute on functions to example_app, example_user;

-- Create schema 'test'. We install our database tests in this schema. Dedicated schema for
-- tests is handy, we don't pollute the application schemas, and we can remove all tests easily
-- by just dropping this schema.

create schema if not exists test;
