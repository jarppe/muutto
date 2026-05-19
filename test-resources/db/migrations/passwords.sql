-- muutto:requires: accounts.sql

-- 
-- Add role that can view accounts:
--


do $$
begin
  create role example_user
    with
      login password 'example_user';
  exception when duplicate_object
    then null;
end
$$;

grant usage on schema example to example_user;

grant select on accounts to example_user;

--
-- Add the passwords table for secrets:
--

create table passwords (
  account_id  uuid  not null references accounts (account_id) on delete cascade,
  password    text  not null
);

-- Give access to only example_app, not example_user:

grant
  select,
  insert, 
  update, 
  delete 
on passwords 
  to example_app;
