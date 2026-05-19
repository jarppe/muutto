-- 
-- Initialize database:
--

alter default privileges revoke execute on functions from public; 

do $$
begin
  create role example_app
    with
      login password 'example_auth';
  exception when duplicate_object
    then null;
end
$$;

create schema example;

do $$
begin
  execute format('alter database %I set search_path to example, pgunit, test, public', current_database());
end
$$;

grant usage on schema example to example_app;
