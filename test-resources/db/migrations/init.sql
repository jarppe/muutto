-- 
-- Initialize database:
--

alter default privileges revoke execute on functions from public; 

do $$
begin
  create role example_auth
    with
      login password 'example_auth';
  exception when duplicate_object
    then null;
end
$$;

do $$
begin
  execute format('alter database %I set timezone to UTC', current_database());
  execute format('alter database %I set search_path to example, pgunit, test, public', current_database());
end
$$;

create schema example;
