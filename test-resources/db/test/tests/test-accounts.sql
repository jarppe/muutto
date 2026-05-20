--
-- Test suite setup and teardown:
--

-- muutto:search-path: example test pgunit public


create or replace function test.test_setup_accounts()
  returns void
  language plpgsql
as $$
begin
  insert into accounts (username, email) values ('tiger', 'tiger@example.com');
end;
$$;

create or replace function test.test_teardown_accounts()
  returns void
  language plpgsql
as $$
begin
  delete from accounts where email = 'tiger@example.com';
end;
$$;

--
-- Tests:
--

create or replace function test.test_case_accounts_app_role()
  returns void
  language plpgsql
as $$
declare
  found_username text;
begin
  set role example_app;
  select username into found_username from accounts where email = 'tiger@example.com';
  perform pgunit.test_assertTrue('account found', found_username = 'tiger');
end;
$$;


create or replace function test.test_case_accounts_user_role()
  returns void
  language plpgsql
as $$
declare
  found_username text;
begin
  set role example_user;
  select username into found_username from accounts where email = 'tiger@example.com';
  perform pgunit.test_assertTrue('account found', found_username = 'tiger');
end;
$$;

