--
-- Test suite setup and teardown:
--


create or replace function test.test_setup_password()
  returns void
  language plpgsql
as $$
begin
  insert into accounts (username, email) values ('tiger', 'tiger@example.com');
  insert into passwords (account_id, password) values 
    (
      (select account_id from accounts where email = 'tiger@example.com'),
      'hunter2'
    );
end;
$$;

create or replace function test.test_teardown_password()
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

create or replace function test.test_case_password_app_role()
  returns void
  language plpgsql
as $$
declare
  found_password text;
begin
  set role example_app;
  select password into found_password 
  from
    passwords
  join 
    accounts on accounts.account_id = passwords.account_id
  where
    email = 'tiger@example.com';
  perform pgunit.test_assertTrue('got password', found_password = 'hunter2');
end;
$$;


create or replace function test.test_case_password_user_role()
  returns void
  language plpgsql
as $$
declare
  found_password text;
begin
  set role example_user;
  
  select password into found_password 
  from
    passwords
  join 
    accounts on accounts.account_id = passwords.account_id
  where
    email = 'tiger@example.com';
  
  perform pgunit.test_fail('should throw exception');

  exception 
    when others then
      perform pgunit.test_assertTrue(sqlstate = '42501');
      perform pgunit.test_assertTrue(sqlerrm = 'permission denied for table passwords');
end;
$$;
